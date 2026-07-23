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
package pt.inesctec.ids.broker.util;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import ids.messaging.util.IdsMessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;

import java.io.IOException;
import java.net.URI;

public class MessageUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageUtil.class);

  public static <T> T deserialize(String header, Class<?> classToD) throws InvalidValueException{
    Serializer serializer=new Serializer();
    try {
      return (T) serializer.deserialize(header, classToD);
    } catch (IOException e) {
      LOGGER.error(e.toString());
      throw new InvalidValueException("Could not parse message!: " +e.getMessage());
    }

  }

  public static Message buildMessageProcessedNotificationMessage(Message message, DynamicAttributeToken dat, Connector connector) {
    LOGGER.debug("Building MessageProcessedNotificationMessage to return ... ");
    Message headerToReturn=new MessageProcessedNotificationMessageBuilder()
        ._securityToken_(dat)
        ._correlationMessage_(message.getId())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_(connector.getOutboundModelVersion())
        ._senderAgent_(connector.getId())
        ._transferContract_(message.getTransferContract())
        .build();
    LOGGER.debug("Building MessageProcessedNotificationMessage to return ... OK");

    return headerToReturn;

  }


  public static ResultMessage buildResultMessage(Message message, DynamicAttributeToken dat, Connector connector){
    return new ResultMessageBuilder()
        ._securityToken_(dat)
        ._correlationMessage_(message.getId())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_(connector.getOutboundModelVersion())
        ._senderAgent_(connector.getId())
        .build();

  }

  public static LogMessage buildLogMessages(String connectorID, Connector connector, DynamicAttributeToken dat)  {
    return new LogMessageBuilder()
            ._securityToken_(dat)
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(URI.create(connectorID))
            ._modelVersion_(connector.getOutboundModelVersion())
            ._senderAgent_(URI.create(connectorID))
            .build();

  }

  public static RejectionMessage buildRejectionMessage(Message message, RejectionReason rejectionReason, DynamicAttributeToken dat, Connector connector) {
    LOGGER.debug("Creating RejectionMessage...");
    return new RejectionMessageBuilder()
        ._securityToken_(dat)
        ._correlationMessage_(message.getId())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_(connector.getOutboundModelVersion())
        ._senderAgent_(connector.getId())
        ._rejectionReason_(rejectionReason)
        ._transferContract_(message.getTransferContract())
        .build();

  }

  public static RejectionMessage buildRejectionMessage(URI correlationID, RejectionReason rejectionReason, DynamicAttributeToken dat, Connector connector) {
    LOGGER.debug("Creating RejectionMessage...");
    return new RejectionMessageBuilder()
        ._securityToken_(dat)
        ._correlationMessage_(correlationID)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_(connector.getOutboundModelVersion())
        ._senderAgent_(connector.getId())
        ._rejectionReason_(rejectionReason)
        .build();

  }

}
