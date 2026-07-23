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
package pt.inesctec.ids.broker.builder;

import com.jayway.jsonpath.DocumentContext;
import de.fraunhofer.iais.eis.DescriptionRequestMessage;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.RequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.dto.Headers;


public class HeadersBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(HeadersBuilder.class);


  private String securitytoken;
  private String modelversion;
  private String issued;
  private String senderagent;
  private String issuerconnector;
  private String correlationmessage;

  private String rejectionReason;

  private String recipientConnector;
  private String recipientAgent;
  private String link;

  public HeadersBuilder() {
  }

  public HeadersBuilder rejectionReason(String rejectionReason) {
    this.rejectionReason=rejectionReason;
    return this;
  }

  public HeadersBuilder recipientConnector(String recipientConnector) {
    this.recipientConnector=recipientConnector;
    return this;
  }

  public HeadersBuilder recipientAgent(String recipientAgent) {
    this.recipientAgent=recipientAgent;
    return this;
  }

  public HeadersBuilder link(String link) {
    this.link=link;
    return this;
  }

  public Headers build(DocumentContext ctx) {
    return new Headers(ctx, rejectionReason, recipientConnector, recipientAgent, link);
  }

  public Headers build(DescriptionRequestMessage message) {
    return new Headers(message, rejectionReason, recipientConnector, recipientAgent, link);
  }

  public Headers build(Message message) {
    return new Headers(message, rejectionReason, recipientConnector, recipientAgent, link);
  }

}
