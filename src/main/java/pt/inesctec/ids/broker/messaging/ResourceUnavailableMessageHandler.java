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
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.ConnectorUnavailableException;
import pt.inesctec.ids.broker.exceptions.ResourceNotFoundException;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.util.MessageUtil;
import pt.inesctec.ids.broker.util.ResourceUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;


@Component
@SupportedMessageType(ResourceUnavailableMessageImpl.class)
public class ResourceUnavailableMessageHandler implements MessageHandler<ResourceUnavailableMessageImpl> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUnavailableMessageHandler.class);

  private final Connector connector;
  private final DapsTokenProvider provider;

  private BrokerService brokerService;
  private DynamicAttributeToken dat;

  public ResourceUnavailableMessageHandler(final ConfigContainer container,
                                           final DapsTokenProvider provider, BrokerService brokerService) {
    this.connector = container.getConnector();
    this.provider = provider;
    this.brokerService=brokerService;
  }

  /**
   * This message implements the logic that is needed to handle the message. As it just returns the input as string the messagePayload-InputStream is converted to a String.
   *
   * @param resourceUnavailableMessage The ResourceUnavailableMessage (part one of the multipart message) containing interesting meta data.
   * @param messagePayload The received payload that has to be handled here - The Connector Self-description
   * @return received payload as string, wrapped inside a @{@link BodyResponse}
   */
  @Override
  public MessageResponse handleMessage(ResourceUnavailableMessageImpl resourceUnavailableMessage, MessagePayload messagePayload) {
    LOGGER.debug("Received a ResourceUnavailableMessage!");

    // Initialize DAT to return on header
    try {
      dat=provider.getDAT();
    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      LOGGER.error("Error while retrieving DAT...");
      LOGGER.error("... " + e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }

    try {

      URI connectorID=resourceUnavailableMessage.getIssuerConnector();
      URI resourceID=resourceUnavailableMessage.getAffectedResource();

      LOGGER.debug("Resource to remove: "+resourceID);

      // Returns the updated model (it includes the Connector, Endpoints, ...)
      Model updatedModel=brokerService.removeResourceFromFuseki(connectorID, resourceID);

      ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
      updatedModel.write(outputStream, "TURTLE");
      LOGGER.debug("Model to Remove: ");
      LOGGER.debug(outputStream.toString());

      Message message = MessageUtil.buildMessageProcessedNotificationMessage(resourceUnavailableMessage, dat, connector);
      return BodyResponse.create(message, "");
    }
    catch (ConnectorUnavailableException | ResourceNotFoundException e){
      // TODO: Fiquei a ver se valerá a pena rejeitar ou não
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(resourceUnavailableMessage, RejectionReason.NOT_FOUND, dat, connector), e.getMessage());
      //return ErrorResponse.withDefaultHeader(RejectionReason.BAD_PARAMETERS, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }
    catch (BadRequestException e){
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(resourceUnavailableMessage, RejectionReason.BAD_PARAMETERS, dat, connector), e.getMessage());
    }
    catch (Exception e) {
      LOGGER.error("Error while handling message...");
      LOGGER.error("... "+e.getMessage());
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(resourceUnavailableMessage, RejectionReason.INTERNAL_RECIPIENT_ERROR, dat, connector), e.getMessage());
      //return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }
  }
}