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
package pt.inesctec.ids.broker.controller;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import de.fraunhofer.iais.eis.*;
import ids.messaging.core.daps.*;
import ids.messaging.dispatcher.filter.PreDispatchingFilterException;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.dto.ResponseMessage;
import pt.inesctec.ids.broker.exceptions.InvalidTokenException;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;
import pt.inesctec.ids.broker.service.ControllerService;
import pt.inesctec.ids.broker.service.DescriptionService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.MessageUtil;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;


@RestController
@ApiResponse
@Tag(name = "5. Multipart", description = "API for Multipart")
@RequiredArgsConstructor
@RequestMapping("/api/ids/data")
public class MultipartController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultipartController.class);

    private DescriptionService service;
    @Autowired
    private FusekiConnectionAccessor accessor;

    @Autowired
    private ControllerService controllerService;

    //The current connector configuration.
    private final @NonNull BrokerSelfDescription brokerSelfDescription;

    private final DapsTokenProvider provider;


    // Controller that handles the Multipart DescriptionRequestMessage implemented in the PlugFest. It needs to be handles separatedly, since the IDS code cannot handle multipart/mixed
    @SecurityRequirements
    @PostMapping(value = {""}, produces = {MediaType.MULTIPART_MIXED_VALUE},
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Get the public IDS self-description.", responses = {
        @ApiResponse(responseCode = "200", description = "successful operation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @ResponseBody
    public ResponseEntity<Object> getPublicSelfDescription(
        @RequestHeader final Map<String, String> header,
        @RequestPart("header") String headerMessage,
        HttpServletRequest request) throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {

        service=new DescriptionService(accessor);

        // 1. Deserialize the Message to send in the messageDispatcher and to Check that the Received Message is valid (JSON)
        // 2. Send message to the Dispatcher

        LOGGER.debug("Checking Message type ");

        Message deserializedMessage = null;
        try {
            deserializedMessage= MessageUtil.deserialize(headerMessage, Message.class);
        } catch (InvalidValueException e) {
            LOGGER.error(e.getMessage());
            // If the received header cannot be deserialized(i.e., it is an invalid JSON) throw RejectionMessage
            DocumentContext ctx = JsonPath.parse(headerMessage);

            ResponseMessage responseMessage=new ResponseMessage(
                MessageUtil.buildRejectionMessage(URI.create(ctx.read("$.['@id']", String.class)),
                    RejectionReason.MALFORMED_MESSAGE,
                    provider.getDAT(),
                    brokerSelfDescription.getConnector())
                , "Could not parse incoming message!");
            LOGGER.warn("Could not parse incoming message!");
            LOGGER.debug(e.getMessage());
            return responseMessage.createResponseEntity(HttpStatus.BAD_REQUEST);
        }

        LOGGER.debug("Message is of type ...");
        LOGGER.debug("..." + deserializedMessage.getClass());

        MessageResponse messageResponse;
        try {
            // The payload should always be null. Sends the Message to the Handler
            // This already checks the Dat
            messageResponse=controllerService.process(deserializedMessage);
        }
        catch (PreDispatchingFilterException e) {
            ResponseMessage responseMessage=new ResponseMessage(MessageUtil.buildRejectionMessage(deserializedMessage, RejectionReason.MALFORMED_MESSAGE, provider.getDAT(), brokerSelfDescription.getConnector()), e.getMessage());
            return responseMessage.createResponseEntity(HttpStatus.NOT_ACCEPTABLE);
        }
        catch (InvalidTokenException e) { // If the Token Format is invalid
            LOGGER.error(e.toString());
            ResponseMessage responseMessage=new ResponseMessage(MessageUtil.buildRejectionMessage(deserializedMessage, RejectionReason.NOT_AUTHENTICATED, provider.getDAT(), brokerSelfDescription.getConnector()), e.getMessage());
            return responseMessage.createResponseEntity(HttpStatus.UNAUTHORIZED);
        }


        Map<String, Object> response = service.getMapFromMessageResponse(messageResponse);
        ResponseMessage responseMessage=new ResponseMessage(response);


        if(controllerService.isSuccessResponse(response, DescriptionResponseMessage.class))
        {
            System.err.println(responseMessage.getHeader().toString());

            // Perfect scenario
            return responseMessage.createResponseEntity(HttpStatus.OK);
        }
        else{
            return responseMessage.createResponseEntity(HttpStatus.UNAUTHORIZED);
        }
    }
}
