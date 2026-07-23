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
import ids.messaging.handler.message.MessageHandlerException;
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
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.util.MessageUtil;

/***
 *
 * Behaviour:
 *    - If a Connector is not registered on the Broker at all: Error message:
 *                     - "Connector not found with ID 'https://circthread.com/ids/connector/id/localconnector'. Did you send a ConnectorUpdateMessage yet?"
 *    - If a Connector is registered on the Broker, but is already inactive: Error message:
 *                     - "The Connector you are trying to remove ('https://circthread.com/ids/connector/id/localconnector') was not found. Did you send a ConnectorUpdateMessage yet?",
 *    - If a Connector is active: Unregister
 * */
@Component
@SupportedMessageType(ConnectorUnavailableMessageImpl.class)
public class ConnectorUnavailableMessageHandler implements MessageHandler<ConnectorUnavailableMessageImpl> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorUnavailableMessageHandler.class);

  private final Connector connector;
  private final DapsTokenProvider provider;
  private BrokerService brokerService;
  private DynamicAttributeToken dat;

  public ConnectorUnavailableMessageHandler(final ConfigContainer container,
                                            final DapsTokenProvider provider, BrokerService brokerService) {
    this.connector = container.getConnector();
    this.provider = provider;
    this.brokerService=brokerService;
  }


  /**
   * This message implements the logic that is needed to handle the message.
   *
   * @param connectorUnavailableMessage The connectorUpdateMessage (part one of the multipart message) containing interesting meta data.
   * @param messagePayload The received payload that has to be handled here - The Connector Self-description
   * @return received payload as string, wrapped inside a @{@link BodyResponse}
   */
  @Override
  public MessageResponse handleMessage(ConnectorUnavailableMessageImpl connectorUnavailableMessage, MessagePayload messagePayload) throws MessageHandlerException {
    LOGGER.info("Received a ConnectorUnavailableMessage!");

    LOGGER.info("Unregistering Connector "+connectorUnavailableMessage.getIssuerConnector()+"...");

    // Initialize DAT to return on header
    try {
      dat=provider.getDAT();
    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      LOGGER.error("Error while retrieving DAT...");
      LOGGER.error("... " + e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }

    try {

      brokerService.makeConnectorUnavailable(connectorUnavailableMessage.getIssuerConnector());

      final var message = MessageUtil.buildMessageProcessedNotificationMessage(connectorUnavailableMessage, dat, connector);
      return BodyResponse.create(message, "");
    }
    catch (ResourceNotFoundException e){
      LOGGER.error("The Connector was not found");
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(connectorUnavailableMessage, RejectionReason.NOT_FOUND, dat, connector), e.getMessage());
    }
    catch (InvalidValueException e){
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(connectorUnavailableMessage, RejectionReason.NOT_FOUND, dat, connector), e.getMessage());

    }
    catch (Exception e) {
      LOGGER.error("Error while handling message...");
      LOGGER.error("... "+e.getMessage());
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(connectorUnavailableMessage, RejectionReason.INTERNAL_RECIPIENT_ERROR, dat, connector), e.getMessage());
    }
  }
}