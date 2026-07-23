/* Copyright (C) [2023-2026], INESC TEC
 *
 * dataspace-metadata-broker
 * Implements a central repository registry and discovery service for data providers and consumers within an International Data Spaces (IDS) ecosystem.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
 * CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations 
 * under the License.
 *
 * You can reach INESC TEC Technology Transfer Office (TTO) at tech-transfer@inesctec.pt, or
 * Campus da Faculdade de Engenharia da Universidade do Porto
 * Rua Dr. Roberto Frias
 * 4200-465 Porto
 * Portugal 
 */
package pt.inesctec.ids.broker.messaging;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.component.core.RejectMessageException;
import ids.messaging.core.config.ConfigContainer;
import ids.messaging.core.daps.ConnectorMissingCertExtensionException;
import ids.messaging.core.daps.DapsConnectionException;
import ids.messaging.core.daps.DapsEmptyResponseException;
import ids.messaging.core.daps.DapsTokenProvider;
import ids.messaging.handler.message.MessageHandler;
import ids.messaging.handler.message.MessagePayload;
import ids.messaging.handler.message.SupportedMessageType;
import ids.messaging.response.BodyResponse;
import ids.messaging.response.ErrorResponse;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import lombok.NonNull;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.exceptions.IsSelfDescriptionException;
import pt.inesctec.ids.broker.service.*;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.MessageUtil;


import java.io.*;
import java.net.URI;
import java.util.Optional;


@Component
@SupportedMessageType(DescriptionRequestMessageImpl.class)
public class DescriptionRequestMessageHandler implements MessageHandler<DescriptionRequestMessageImpl> {
  private static final Logger LOGGER = LoggerFactory.getLogger(DescriptionRequestMessageHandler.class);

  private final Connector connector;
  private final DapsTokenProvider provider;
  private final FusekiConnectionAccessor accessor;
  private final @NonNull BrokerSelfDescription brokerSelfDescription;

  private final String catalogUri;

  private DynamicAttributeToken dat;

  public DescriptionRequestMessageHandler(final ConfigContainer container,
                                          final DapsTokenProvider provider, FusekiConnectionAccessor accessor, @NonNull BrokerSelfDescription brokerSelfDescription, @Qualifier("catalogURI") String catalogUri) {
    this.connector = container.getConnector();
    this.provider = provider;
    this.accessor = accessor;
    this.brokerSelfDescription = brokerSelfDescription;
    this.catalogUri = catalogUri;
  }


  /**
   * This message implements the logic that is needed to handle the message.
   *
   * @param requestMessage The DescriptionRequestMessage (part one of the multipart message) containing interesting meta data.
   * @param messagePayload The received payload that has to be handled here - The Connector Self-description
   * @return received payload as string, wrapped inside a @{@link BodyResponse}
   */
  @Override
  public MessageResponse handleMessage(DescriptionRequestMessageImpl requestMessage, MessagePayload messagePayload) {
    LOGGER.info("Received a DescriptionRequestMessage!");

    DescriptionService descriptionService=new DescriptionService(accessor);
    // This message has an additional property (depth) that defines the de+th to which we should go into the database. By default, the IDS Conector sets it to 10.

    // If no depth property is defined, 0 is used as default value
    int depth=descriptionService.getDepth(requestMessage);
    URI elementID = requestMessage.getRequestedElement();

    LOGGER.debug("Requested Element: "+elementID);
    LOGGER.debug("Catalogue URI: " + catalogUri);

    // Initialize DAT to return on header
    try {
      dat=provider.getDAT();
    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      LOGGER.error("Error while retrieving DAT...");
      LOGGER.error("... " + e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }

    // Creates successfull return message
    final var descriptionResponseMessage= buildDescriptionResponseMessage(requestMessage, dat);

    // Retrieve output Format from Headers
    RDFFormat outputFormat=descriptionService.returnOutputFormat(requestMessage);


    try {
      // Build Query and Get requested Model
      Model model=descriptionService.description(catalogUri, elementID, accessor, depth);

      // Write the output to the expected Return format
      ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
      RDFWriter.create()
          .source(model)
          .format(outputFormat)
          .output(outputStream);

      LOGGER.debug(outputStream.toString());

      // This list should only have 1 value and the Optional should never be emtpy
      Optional<RDFNode> elementType=model.listStatements()
          // Keep all statements where:
          .filterKeep(statement ->
              // Subject is the elementID (with or without '/' -> Because of /connectors/)
              (statement.getSubject().equals(ResourceFactory.createResource(String.valueOf(elementID)))
                  || statement.getSubject().equals(ResourceFactory.createResource(String.valueOf(elementID+"/")))
              )
                  // Predicate is '@type'
                  && statement.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")))
          // Save only the object
          .mapWith(Statement::getObject)
          .toList().stream().findFirst();


      //Attach custom properties:
      //  Returned element type
      elementType.ifPresent(rdfNode -> descriptionResponseMessage.setProperty("elementType", rdfNode.toString()));
      // Serialization  used. This is important for Content Negotiation headers
      descriptionResponseMessage.setProperty("Serialization", outputFormat.toString());

      // Send message
      return BodyResponse.create(descriptionResponseMessage, outputStream.toString());

    } catch (IsSelfDescriptionException isDescription){
      // Add additional properties
      descriptionResponseMessage.setProperty("elementType", brokerSelfDescription.getSelfDescription().getClass().getSimpleName());
      descriptionResponseMessage.setProperty("Serialization", outputFormat.toString());

      // IF there is no elementID it means to return the Broker public self-description
      return BodyResponse.create(descriptionResponseMessage, brokerSelfDescription);
    }
    catch (RejectMessageException e) {
      // It happens when the requested resource was not found
      LOGGER.error(e.getMessage());
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(requestMessage, RejectionReason.NOT_FOUND, dat, connector), e.getRejectionPayload());
    }
  }

  private DescriptionResponseMessage buildDescriptionResponseMessage(DescriptionRequestMessageImpl message, DynamicAttributeToken dat) {
    LOGGER.info("Creating DescriptionResponseMessage...");
    return new DescriptionResponseMessageBuilder()
        ._securityToken_(dat)
        ._correlationMessage_(message.getId())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_(this.connector.getOutboundModelVersion())
        ._senderAgent_(connector.getId())
        ._transferContract_(message.getTransferContract())
        .build();

  }

}