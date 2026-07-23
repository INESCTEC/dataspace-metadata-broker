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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;
import pt.inesctec.ids.broker.exceptions.ResourceNotFoundException;
import pt.inesctec.ids.broker.json.ErrorMessage;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.util.ConnectorUtil;
import pt.inesctec.ids.broker.util.MessageUtil;
import pt.inesctec.ids.broker.util.ResourceUtil;

import java.net.URI;


@Component
@SupportedMessageType(ResourceUpdateMessageImpl.class)
public class ResourceUpdateMessageHandler implements MessageHandler<ResourceUpdateMessageImpl> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUpdateMessageHandler.class);

  private final Connector connector;
  private final DapsTokenProvider provider;

  private BrokerService brokerService;

  public ResourceUpdateMessageHandler(final ConfigContainer container,
                                      final DapsTokenProvider provider, BrokerService brokerService) {
    this.connector = container.getConnector();
    this.provider = provider;
    this.brokerService=brokerService;
  }


  /**
   * This message implements the logic that is needed to handle the message. As it just returns the input as string the messagePayload-InputStream is converted to a String.
   *
   * @param resourceUpdateMessage The ResourceUpdateMessage (part one of the multipart message) containing interesting meta data.
   * @param messagePayload The received payload that has to be handled here - The Connector Self-description
   * @return received payload as string, wrapped inside a @{@link BodyResponse}
   */
  @Override
  public MessageResponse handleMessage(ResourceUpdateMessageImpl resourceUpdateMessage, MessagePayload messagePayload) {
    LOGGER.info("Received a ResourceUpdateMessage!");

    // Get the DAT
    DynamicAttributeToken dat;
    try {
      dat=provider.getDAT();
    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      LOGGER.error("Error while retrieving DAT...");
      LOGGER.error("... "+e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }

    try {

      URI connectorID=resourceUpdateMessage.getIssuerConnector();
      URI affectedResource=resourceUpdateMessage.getAffectedResource();

      // Build Header to Return
      Message message = MessageUtil.buildMessageProcessedNotificationMessage(resourceUpdateMessage, dat, connector);

      // Deserialize Resource
      Resource json= ResourceUtil.deserialize(messagePayload.getUnderlyingInputStream());
      // TODO 11/10: Check that the affectedResource é a mesma que json.getID()

      // Add Resource to Fuseki
      String resourceInternalID=brokerService.addResourceToFuseki(json, connectorID);
      // It should return the intermalURI of the Resource, not the graphName
      message.setProperty("Location", "<"+resourceInternalID+">"); // To Stay consistent with the IDSA Broker

      return BodyResponse.create(message, json);
    }
    catch (ResourceNotFoundException e){
      LOGGER.error(e.getMessage());
      return ErrorResponse.create(this.createRejectionReturnMessage(resourceUpdateMessage, dat, RejectionReason.BAD_PARAMETERS), e.getMessage());
    }
    catch (InvalidValueException e){
      LOGGER.error(e.getMessage());
      return ErrorResponse.create(this.createRejectionReturnMessage(resourceUpdateMessage, dat, RejectionReason.NOT_FOUND), e.getMessage());
    }
    // It should never happen
    catch (Exception e) {
      LOGGER.error("Error while handling message...");
      LOGGER.error("... "+e.getMessage());
      return ErrorResponse.create(this.createRejectionReturnMessage(resourceUpdateMessage, dat, RejectionReason.INTERNAL_RECIPIENT_ERROR), e.getMessage());
    }
  }

  public RejectionMessage createRejectionReturnMessage(ResourceUpdateMessageImpl message, DynamicAttributeToken dat, RejectionReason rejectionReason) {
      return new RejectionMessageBuilder()
          ._securityToken_(dat)
          ._correlationMessage_(message.getId())
          ._issued_(IdsMessageUtils.getGregorianNow())
          ._issuerConnector_(connector.getId())
          ._modelVersion_(this.connector.getOutboundModelVersion())
          ._senderAgent_(connector.getId())
          ._rejectionReason_(rejectionReason)
          .build();

  }
}