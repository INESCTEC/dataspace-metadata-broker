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
import de.fraunhofer.iais.eis.ids.component.interaction.validation.ShaclValidator;
import ids.messaging.common.DeserializeException;
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
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.util.ConnectorUtil;
import pt.inesctec.ids.broker.util.MessageUtil;
import pt.inesctec.ids.broker.util.ResourceUtil;

import java.io.IOException;


@Component
@SupportedMessageType(ConnectorUpdateMessageImpl.class)
public class ConnectorUpdateMessageHandler implements MessageHandler<ConnectorUpdateMessageImpl> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorUpdateMessageHandler.class);

  private final Connector connector;
  private final DapsTokenProvider provider;

  private BrokerService brokerService;
  private DynamicAttributeToken dat;

  public ConnectorUpdateMessageHandler(final ConfigContainer container,
                                       final DapsTokenProvider provider, BrokerService brokerService) {
    this.connector = container.getConnector();
    this.provider = provider;
    this.brokerService=brokerService;
  }


  /**
   * This message implements the logic that is needed to handle the message. As it just returns the input as string the messagePayload-InputStream is converted to a String.
   *
   * @param connectorUpdateMessage The connectorUpdateMessage (part one of the multipart message) containing interesting meta data.
   * @param messagePayload The received payload that has to be handled here - The Connector Self-description
   * @return received payload as string, wrapped inside a @{@link BodyResponse}
   */
  @Override
  public MessageResponse handleMessage(ConnectorUpdateMessageImpl connectorUpdateMessage, MessagePayload messagePayload) {
    LOGGER.info("Received a ConnectorUpdateMessage!");

    // Initialize DAT to return on header
    try {
      dat=provider.getDAT();
    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      LOGGER.error("Error while retrieving DAT...");
      LOGGER.error("... " + e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }

    try {
      Connector json=ConnectorUtil.deserialize(messagePayload.getUnderlyingInputStream());

      // If the Connector also has a Resource, check if it is correct (e.g., do not lack any mandatory field)
      json.getResourceCatalog().forEach(catalog ->
          catalog.getOfferedResource().forEach(resource -> {
            MessageUtil.deserialize(resource.toRdf(), de.fraunhofer.iais.eis.Resource.class);
            ResourceUtil.checkDataModel(resource);
          })
      );

      String graphName=brokerService.addConnectorToFuseki(json);

      final var message = MessageUtil.buildMessageProcessedNotificationMessage(connectorUpdateMessage, dat, connector);
      message.setProperty("Location", "<"+graphName+">"); // To Stay consistent with the IDSA Broker
      //LOGGER.info(String.valueOf(message));

      return BodyResponse.create(message, json);
    }
    catch (InvalidValueException e){
      // It is catched when deserializing the resource
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(connectorUpdateMessage, RejectionReason.BAD_PARAMETERS, dat, connector), e.getMessage());
    }
    catch (BadRequestException e){
      // It is catched when deserializing the Connector
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(connectorUpdateMessage, RejectionReason.BAD_PARAMETERS, dat, connector), e.getMessage());
    }
    catch (Exception e) {
      LOGGER.error("Error while handling message...");
      LOGGER.error("... "+e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }
  }
}