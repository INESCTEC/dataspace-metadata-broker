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
package pt.inesctec.ids.broker.service;

import de.fraunhofer.iais.eis.*;
import ids.messaging.common.DeserializeException;
import ids.messaging.common.SerializeException;
import ids.messaging.core.daps.*;
import ids.messaging.dispatcher.MessageDispatcher;
import ids.messaging.dispatcher.filter.PreDispatchingFilterException;
import ids.messaging.protocol.http.IdsHttpService;
import ids.messaging.protocol.http.ShaclValidatorException;
import ids.messaging.protocol.multipart.parser.MultipartParseException;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.InfomodelMessageBuilder;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.MultipartBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import pt.inesctec.ids.broker.builder.HeadersBuilder;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.dto.Headers;
import pt.inesctec.ids.broker.exceptions.InvalidTokenException;
import pt.inesctec.ids.broker.util.MessageUtil;

import java.io.IOException;
import java.util.Map;

import static pt.inesctec.ids.broker.config.IDSConstants.*;

@Service
@RequiredArgsConstructor
public class ControllerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ControllerService.class);

  @Autowired DescriptionService descriptionService;

  //The current connector configuration.
  @Autowired private @NonNull BrokerSelfDescription brokerSelfDescription;

  @Autowired
  private DapsTokenProvider provider;

  @Autowired
  private MessageDispatcher messageDispatcher; // Existing IDS message handler

  @Autowired private DapsValidator dapsValidator;


  // Service for ids communication.
  @Autowired
  private IdsHttpService idsHttpService;


  //@Autowired private SerializerProvider serializerProvider;


  public ResponseEntity<Object> checkDat(DapsValidator dapsValidator, Message idsMessage){
    boolean isDatValid;
    Headers headerToReturn;
    try {
      isDatValid = dapsValidator.checkDat(idsMessage.getSecurityToken());
    }catch (UnsupportedJwtException e){
      // Add Headers
      headerToReturn=new HeadersBuilder()
          .rejectionReason(RejectionReason.NOT_AUTHENTICATED.toString())
          .build(idsMessage);

      // If the Token Format is invalid
      return new ResponseEntity<>("Error verifying token.", CollectionUtils.toMultiValueMap(headerToReturn.toMap()), HttpStatus.UNAUTHORIZED);
    }

    if(!isDatValid){
      // Add Headers
      headerToReturn=new HeadersBuilder()
          .rejectionReason(RejectionReason.NOT_AUTHENTICATED.toString())
          .build(idsMessage);

      // If the Token Value is invalid
      // java.lang.IllegalArgumentException: JWT String argument cannot be null or empty.
      return new ResponseEntity<>("Error processing token.", CollectionUtils.toMultiValueMap(headerToReturn.toMap()), HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok().build();

  }

  public boolean isSuccessResponse(Map<String, Object> response, Class<?> expectedResponseType){
    Message expectedMessage= MessageUtil.deserialize(response.get(HEADER).toString(), Message.class);

    // If it is a GET
    if(expectedResponseType.isAssignableFrom(DescriptionResponseMessage.class)){
      return expectedMessage instanceof DescriptionResponseMessage;
    }

    // If it is Registering a Connector (POST)
    if(expectedResponseType.isAssignableFrom(MessageProcessedNotificationMessage.class)){
      return expectedMessage instanceof MessageProcessedNotificationMessage;
    }
    else return false;

  }

  public MessageResponse process(Message idsMessage) throws InvalidTokenException, PreDispatchingFilterException {
    try {
      // The payload should always be null. Sends the Message to the Handler
      MessageResponse messageResponse=messageDispatcher.process(idsMessage, null);

      LOGGER.debug("Message Response ...");
      LOGGER.debug("..." + messageResponse);

      return messageResponse;

    }
    catch (IllegalArgumentException | ExpiredJwtException e){
      LOGGER.debug(e.toString());
      //java.lang.IllegalArgumentException: JWT String argument cannot be null or empty.
      throw new InvalidTokenException("Error processing token.");
    }
    catch (UnsupportedJwtException e){
      LOGGER.debug(e.toString());
      // If the Token Format is invalid
      throw new InvalidTokenException("Error verifying token.");
      //return new ResponseEntity<>("Error verifying token.", HttpStatus.UNAUTHORIZED);
    }
  }

  public void buildDescriptionRequestMessage(){

  }

  public void buildSOM(){

  }

  private Map<String, String> sendAndCheckDat(Message message)  {
    MultipartBody body = null;
    try {
      body = InfomodelMessageBuilder.messageWithString(message, "");
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }

    try {
      Map<String, String> response = idsHttpService.sendAndCheckDat(body, brokerSelfDescription.getConnector().getHasDefaultEndpoint().getAccessURL());
      return response;
    } catch (DeserializeException | ShaclValidatorException | ClaimsException | MultipartParseException |
             IOException e) {
      throw new RuntimeException(e);
    }
  }
}
