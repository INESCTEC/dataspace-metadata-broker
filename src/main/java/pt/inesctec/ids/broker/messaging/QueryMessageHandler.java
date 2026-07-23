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
import org.apache.jena.query.QuerySolution;
import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.service.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.MessageUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/***
 *
 * Return Types:
 *    - SELECT Query:
 *             - TSV (Default), CSV, JSON, Text (It is displayed as a table)
 *    - CONSTRUCT Query:
 *             - JSON_LD (Default - RDF Graph), TURTLE, TRIPLES
 *    - DESCRIBE Query:
 *             - Turtle (Default)
 *    - ASK Query:
 *             - Boolean
 * */
@Component
@SupportedMessageType(QueryMessageImpl.class)
public class QueryMessageHandler implements MessageHandler<QueryMessageImpl> {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryMessageHandler.class);

  private final Connector connector;
  private final DapsTokenProvider provider;
  private BrokerService service;
  private DynamicAttributeToken dat;

  public QueryMessageHandler(final ConfigContainer container,
                             final DapsTokenProvider provider, BrokerService brokerService) {
    this.connector = container.getConnector();
    this.provider = provider;
    this.service=brokerService;

  }


  /**
   * This message implements the logic that is needed to handle the message.
   *
   * @param queryMessage The queryMessageImpl (part one of the multipart message) containing interesting meta data.
   * @param messagePayload The received payload that has to be handled here
   * @return received payload as string, wrapped inside a @{@link BodyResponse}
   */
  @Override

  public MessageResponse handleMessage(final QueryMessageImpl queryMessage, final MessagePayload messagePayload) {
    LOGGER.info("Received a QueryMessage!");
    Object acceptedType= ResponseType.TSV; // Default Value
    LOGGER.debug(queryMessage.toRdf());

    // Initialize DAT to return on header
    try {
      dat=provider.getDAT();
    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      LOGGER.error("Error while retrieving DAT...");
      LOGGER.error("... " + e.getMessage());
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }
    try {

      final var receivedPayload = IOUtils.toString(messagePayload.getUnderlyingInputStream(),
              StandardCharsets.UTF_8.name()) ;

      LOGGER.debug("Creating message...");
      //LOGGER.info(String.valueOf(message));
      Message message = MessageUtil.buildResultMessage(queryMessage, dat, connector);

      // Sees if there is any header
      if(queryMessage.getProperties()!=null){
        if(queryMessage.getProperties().containsKey("https://w3id.org/idsa/core/return-format")){
          acceptedType=queryMessage.getProperties().get("https://w3id.org/idsa/core/return-format");

          if (ResponseType.checkIfExists(acceptedType.toString())){
            // It presuposes that the header is always a string
            LOGGER.debug("Type to return: "+acceptedType);
          }
          else {
            LOGGER.error("Type to return is invalid. The default format for the query type will be returned.");
          }

        }
      }
      // IF the DB is empty it returns []
      LOGGER.debug("Executing Query...");
      String result=service.executeSPARQLQuery(receivedPayload, acceptedType.toString());

      LOGGER.debug(result);

      LOGGER.debug("Sending response back...");

      return BodyResponse.create(message, result);
    }
    catch (BadRequestException e) {
      // This catch catches 'QueryParseException' and 'QueryExceptionHTTP'
      return ErrorResponse.create(MessageUtil.buildRejectionMessage(queryMessage, RejectionReason.BAD_PARAMETERS, dat, connector), e.getMessage());
      //return ErrorResponse.withDefaultHeader(RejectionReason.BAD_PARAMETERS, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }
    catch (Exception e) {
      LOGGER.error("Error while executing query");
      return ErrorResponse.withDefaultHeader(RejectionReason.INTERNAL_RECIPIENT_ERROR, e.getMessage(), connector.getId(), connector.getOutboundModelVersion());
    }
  }
}