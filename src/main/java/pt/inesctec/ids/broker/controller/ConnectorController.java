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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.component.core.RejectMessageException;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import ids.messaging.core.daps.DapsTokenProvider;
import ids.messaging.core.daps.DapsValidator;
import ids.messaging.dispatcher.filter.PreDispatchingFilterException;
import ids.messaging.handler.message.MessagePayloadInputstream;
import ids.messaging.protocol.http.IdsHttpService;
import ids.messaging.response.MessageResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.util.CollectionUtils;
import pt.inesctec.ids.broker.builder.HeadersBuilder;
import pt.inesctec.ids.broker.converter.XMLGregorianCalendarConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.dto.Headers;
import pt.inesctec.ids.broker.exceptions.InvalidTokenException;
import pt.inesctec.ids.broker.exceptions.IsSelfDescriptionException;
import pt.inesctec.ids.broker.messaging.*;
import pt.inesctec.ids.broker.service.ControllerService;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.DescriptionService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.*;

import javax.servlet.http.HttpServletRequest;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static pt.inesctec.ids.broker.config.IDSConstants.*;
import static pt.inesctec.ids.broker.config.ResponseType.*;


@RestController
@ApiResponse
@Tag(name = "2. Connectors", description = "API for Information regarding the Connectors registered on the Broker")
@RequiredArgsConstructor
@RequestMapping("/connectors")
public class ConnectorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorController.class);

    private DescriptionService service;
    @Autowired
    private FusekiConnectionAccessor accessor;

    @Autowired @Qualifier("catalogURI")
    private String catalogUri;

    private final @NonNull BrokerSelfDescription brokerSelfDescription;

    private final DapsTokenProvider provider;
    @Autowired
    private ControllerService controllerService;

    @Autowired private DescriptionRequestMessageHandler descriptionRequestMessageHandler;
    @Autowired private ConnectorUpdateMessageHandler connectorUpdateMessageHandler;

    @Autowired private DapsValidator dapsValidator;

    /**
     * Service for ids communication.
     */
    @Autowired
    private IdsHttpService idsHttpService;

// TODO: Add REST Catalog endpoints - //  https://app.swaggerhub.com/apis/idsa/IDS-Broker/1.3.1#/Catalog


    // TODO: The removeConnector works with the Connectors endpoint, despite the Postman showing the Catalog endpoint. When removing I need to compare the ids-issuerConnector header with the Connector ID

    /**
     * Gets the catalog of active connectors on the Broker.
     * @return ConnectorDTO.
     */
    //@SecurityRequirements
    @GetMapping(value = {"/", "/**"},
        produces = {JSON_LD, TURTLE, RDF_XML, NTRIPLES})
    @Operation(summary = "Get the catalogue of active connectors.", responses = {
        @ApiResponse(responseCode = "200", description = "successful operation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Object> getConnectors(
        @RequestHeader Map<String, String> headers,
        HttpServletRequest request)  {
        // I cannot use pathVariable because of the '/' in the graphName
        LOGGER.debug("Getting the catalog of Connectors..." + request.getRequestURI());

        // Instantiate Variabls
        service = new DescriptionService(accessor);
        XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
        MessageResponse messageResponse;
        ResponseMessage responseHeader;
        Headers headerToReturn;

        LOGGER.debug("Headers: " + headers.toString());

        // Create a 'DescriptionRequestMessage' using the Headers
        DescriptionRequestMessage descriptionRequestMessage;
        try {
             descriptionRequestMessage=new DescriptionRequestMessageBuilder()
                ._securityToken_(
                    new DynamicAttributeTokenBuilder()
                        ._tokenValue_(headers.get("ids-securitytoken"))
                        ._tokenFormat_(TokenFormat.JWT)
                        .build())
                ._issued_(xmlGregorianCalendarConverter.convertString2XMLGregorianCalendar(headers.get("ids-issued")))
                ._issuerConnector_(headers.get("ids-issuerconnector")==null ? null : URI.create(headers.get("ids-issuerconnector")))
                ._modelVersion_(headers.get("ids-modelversion"))
                ._senderAgent_(headers.get("ids-senderagent")==null ? null : URI.create(headers.get("ids-senderagent")))
                .build();
            if(headers.get("ids-recipientconnector")!=null){
                descriptionRequestMessage.setRecipientConnector(List.of(URI.create(headers.get("ids-recipientconnector"))));
            }
            if(headers.get("depth")!=null){
                descriptionRequestMessage.setProperty(DEPTH, headers.get("depth"));
            }
            if(headers.get("accept")!=null){
                descriptionRequestMessage.setProperty("https://w3id.org/idsa/core/accept", new TypedLiteral(headers.get("accept")));
            }
        }catch (ConstraintViolationException  e){
            // Prepares the string to return
            String headerName=ExceptionUtil.getHeaderNamesFromException(e);
            return new ResponseEntity<>("Missing header: "+headerName, HttpStatus.BAD_REQUEST);
        }


        // Set the Requested Element
        // If there is no requestedElement, return the ConnectorCatalog
        if (request.getRequestURI().endsWith("/connectors") || request.getRequestURI().endsWith("/connectors/")) {
            // Set the requestElement
            descriptionRequestMessage.setRequestedElement(URI.create(catalogUri));
        }
        else{
            final String requestedElement = request.getRequestURI()
                .split(request.getContextPath() + "/connectors/")[1];
            // Set the requestElement
            descriptionRequestMessage.setRequestedElement(URI.create(catalogUri+requestedElement));
        }


        try {
            // This already checks the Dat
            messageResponse=controllerService.process(descriptionRequestMessage);
        }
        catch (PreDispatchingFilterException e) {
            // Add Headers
            headerToReturn=new HeadersBuilder()
                .rejectionReason(RejectionReason.MALFORMED_MESSAGE.toString())
                .build(descriptionRequestMessage);
            return new ResponseEntity<>(e.getMessage(), CollectionUtils.toMultiValueMap(headerToReturn.toMap()), HttpStatus.NOT_ACCEPTABLE);

        }
        catch (InvalidTokenException e) { // If the Token Format is invalid
            LOGGER.error(e.toString());
            // Add Headers
            headerToReturn=new HeadersBuilder()
                .rejectionReason(RejectionReason.NOT_AUTHENTICATED.toString())
                .build(descriptionRequestMessage);

            return new ResponseEntity<>(e.getMessage(), CollectionUtils.toMultiValueMap(headerToReturn.toMap()), HttpStatus.UNAUTHORIZED);
        }

        // Call the DescriptionRequestMessageHandler
        /*messageResponse=descriptionRequestMessageHandler.handleMessage((DescriptionRequestMessageImpl)descriptionRequestMessage,
            new MessagePayloadInputstream(IOUtils.toInputStream(""), new ObjectMapper()));*/
        Map<String, Object> response =service.getMapFromMessageResponse(messageResponse);

        DocumentContext ctx = JsonPath.parse(response.get(HEADER).toString());

        LOGGER.debug("Message Response ...");
        LOGGER.debug("..." + messageResponse);

        // Add the Headers from the Response

        // Perfect scenario - add neccessary headers
        if(controllerService.isSuccessResponse(response, DescriptionResponseMessage.class))
        {
            // Get the elementType because the Fraunhoffer Serializer loses the properties 'properties'
            String elementType = ctx.read("$.elementType['@id']", String.class);

            // Perfect scenario - add neccessary headers
            headerToReturn=new HeadersBuilder()
                .link(new TypedLiteral(elementType).getValue())
                .build(ctx);
            return new ResponseEntity<>(response.get(PAYLOAD), CollectionUtils.toMultiValueMap(headerToReturn.toMap()), HttpStatus.OK);
        }
        //It is ids:RejectionMessage
        else{
            responseHeader = MessageUtil.deserialize(response.get(HEADER).toString(),  RejectionMessage.class);
            //LOGGER.error(responseHeader.toString());

            // Error scenario - add neccessary headers
            headerToReturn=new HeadersBuilder()
                .rejectionReason(((RejectionMessage)responseHeader).getRejectionReason().toString())
                .build(ctx);

            // Convert the Rejection Reason to HTTPStatus
            String rejectionReason=((RejectionMessage) responseHeader).getRejectionReason().toString();
            HttpStatus statusToReturn=HttpStatus.valueOf(rejectionReason.toString().substring(rejectionReason.toString().lastIndexOf("/") + 1).trim());

            return new ResponseEntity<>(response.get(PAYLOAD).toString(), CollectionUtils.toMultiValueMap(headerToReturn.toMap()), statusToReturn);
        }

        // Alternative to calling the Handler. Using the code bellow, the IDS handles all verifications. However, it is slower
        //Map<String, String> response=sendAndCheckDat(descriptionRequestMessage);
    }

    /**
     * Register a new connector.
     * @return Connector.
     */
    @SecurityRequirements
    @PostMapping(value = {""},
        produces = {JSON_LD})
    @Operation(summary = "Register a new connector.", responses = {
        @ApiResponse(responseCode = "200", description = "successful operation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Object> registerConnector(
        @RequestHeader Map<String, String> headers,
        @RequestBody String connector,
        HttpServletRequest request)  {

        //TODO: PUT does not exist in the IDSA-Broker. Therefore, it can be a future development

        LOGGER.info("Registering a new connector...");

        service=new DescriptionService(accessor);
        // Instantiate Variabls
        XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
        MessageResponse messageResponse;
        ResponseMessage responseHeader;
        AtomicReference<Headers> headerToReturn = new AtomicReference<>();

        LOGGER.debug("Headers: " + headers.toString());

        // TODO: check the header issuer connector against the payload connectorURI. If not euqal, throw:
        //The issuing connector of this message does not match the connector URI in the payload, violating Same Origin policy.

        ConnectorUpdateMessage messageHeaderToReturn;
        try {
            // Create a 'ConnectorUpdateMessage' using the Headers
            messageHeaderToReturn=new ConnectorUpdateMessageBuilder()
                ._securityToken_(
                    new DynamicAttributeTokenBuilder()
                        ._tokenValue_(headers.get("ids-securitytoken"))
                        ._tokenFormat_(TokenFormat.JWT)
                        .build())
                ._issued_(xmlGregorianCalendarConverter.convertString2XMLGregorianCalendar(headers.get("ids-issued")))
                ._issuerConnector_(headers.get("ids-issuerconnector")==null ? null:URI.create(headers.get("ids-issuerconnector")))
                ._modelVersion_(headers.get("ids-modelversion"))
                ._senderAgent_(headers.get("ids-senderagent")==null ? null:URI.create(headers.get("ids-senderagent")))
                ._affectedConnector_(headers.get("ids-issuerconnector")==null ? null:URI.create(headers.get("ids-issuerconnector")))
                .build();
            if(headers.get("ids-recipientconnector")!=null){
                messageHeaderToReturn.setRecipientConnector(List.of(URI.create(headers.get("ids-recipientconnector"))));
            }
            // TODO 25-09: In the Postman only json-LD is used. Check this. Currently it is sending this to the Broker, but on the COnnectorHandler is not retrieving it
            if(headers.get("accept")!=null){
                messageHeaderToReturn.setProperty("https://w3id.org/idsa/core/accept", new TypedLiteral(headers.get("accept")));
            }
        }
        catch (ConstraintViolationException e){
            // Prepares the string to return
            String headerName=ExceptionUtil.getHeaderNamesFromException(e);
            return new ResponseEntity<>("Missing header: "+headerName, HttpStatus.BAD_REQUEST);

        }

        // Check the ids-securityToken -> This time, it needs to be here, because I need to first check the token
        ResponseEntity<Object> dapsResponse= controllerService.checkDat(dapsValidator, messageHeaderToReturn);
        if(dapsResponse.getStatusCode()==HttpStatus.UNAUTHORIZED){
            return dapsResponse;
        }

        //Check if the provided Connector ID already exists
        ModelService modelService=new ModelService(accessor);
        AdminGraphService adminGraphService=new AdminGraphService(accessor);


        // It needs to be validated here, because the Handler appends if there were any changes. However, to update it it needs to be a PUT
        Optional<String> connectorGraphName=modelService.convertConnectorIDToGraphName(messageHeaderToReturn.getAffectedConnector());
        LOGGER.debug("Connector GraphName "+ connectorGraphName);
        if(connectorGraphName.isPresent()){
            if(adminGraphService.getListOfActiveConnectors().contains(connectorGraphName.get())){
                LOGGER.debug("Connector is already active ");

                // Add Headers
                headerToReturn.set(new HeadersBuilder()
                    .rejectionReason(RejectionReason.TOO_MANY_RESULTS.toString())
                    .build(messageHeaderToReturn));

                LOGGER.info("Registering a new connector... NOK");
                // This is to be equal to the IDSA-Broker
                return new ResponseEntity<>("The connector you are posting already exists. Use PUT instead to overwrite it.", CollectionUtils.toMultiValueMap(headerToReturn.get().toMap()), HttpStatus.CONFLICT);
            }
        }



        // Call the Handler -> This method calls the handler instead of the messageDispatcher, because the Dat validation needs to be done before checking if it a POST is valid
        messageResponse=connectorUpdateMessageHandler.handleMessage((ConnectorUpdateMessageImpl) messageHeaderToReturn,
            new MessagePayloadInputstream(IOUtils.toInputStream(connector), new ObjectMapper()));
        Map<String, Object> response =service.getMapFromMessageResponse(messageResponse);


        DocumentContext ctx = JsonPath.parse(response.get(HEADER).toString());

        LOGGER.debug("Message Response ...");
        LOGGER.debug("..." + messageResponse);

        // Add the Headers from the Response

        // Perfect scenario - add neccessary headers
        if(controllerService.isSuccessResponse(response, MessageProcessedNotificationMessage.class))
        {
            // Perfect scenario - add neccessary headers
            headerToReturn.set(new HeadersBuilder()
                .build(ctx));
            LOGGER.info("Registering a new connector... OK");
            return new ResponseEntity<>(response.get(PAYLOAD), CollectionUtils.toMultiValueMap(headerToReturn.get().toMap()), HttpStatus.OK);
        }
        // It is ids:RejectionMessage
        else{
            responseHeader = MessageUtil.deserialize(response.get(HEADER).toString(),  RejectionMessage.class);
            //LOGGER.error(responseHeader.toString());

            // Error scenario - add neccessary headers
            headerToReturn.set(new HeadersBuilder()
                .rejectionReason(((RejectionMessage) responseHeader).getRejectionReason().toString())
                .build(ctx));

            // Convert the Rejection Reason to HTTPStatus
            String rejectionReason=((RejectionMessage) responseHeader).getRejectionReason().toString();

            HttpStatus statusToReturn;
            try {
                statusToReturn=HttpStatus.valueOf(rejectionReason.toString().substring(rejectionReason.toString().lastIndexOf("/") + 1).trim());
            }
            catch (IllegalArgumentException e){
                statusToReturn=HttpStatus.BAD_REQUEST;
            }

            LOGGER.info("Registering a new connector... NOK");
            return new ResponseEntity<>(response.get(PAYLOAD).toString(), CollectionUtils.toMultiValueMap(headerToReturn.get().toMap()), statusToReturn);
        }

        // Alternative to calling the Handler. Using the code bellow, the IDS handles all verifications. However, it is slower
        //Map<String, String> response=sendAndCheckDat(descriptionRequestMessage);
    }


    // This is not using the DescriptionRequestMessage
    @SecurityRequirements
    @GetMapping(value = { "/ui"},
        produces = {JSON_LD, TURTLE, RDF_XML, NTRIPLES})
    @Operation(summary = "Get the catalogue of active connectors. Used only for the Swagger UI", responses = {
        @ApiResponse(responseCode = "200", description = "successful operation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @ResponseBody
    public ResponseEntity<Object> getConnectors_UI(
        @RequestParam Optional<String> requestedElement,
        @RequestHeader Map<String, String> headers,
        HttpServletRequest request) {

        LOGGER.debug("Getting the catalog of Connectors..." + requestedElement);
        // I do not check for the IDS headers, because this method should only be used on Swagger (To test)

        service = new DescriptionService(accessor);
        StringWriter stringWriter = new StringWriter();
        Model model = null;

        int depth=service.getDepth(headers);
        LOGGER.debug("Headers: " + headers.toString());
        try {
            // If there is no requestedElement, return the ConnectorCatalog
            if (!requestedElement.isPresent()) {
                model = service.description(catalogUri, URI.create(catalogUri), accessor, depth);
            }
            else{
                model = service.description(catalogUri, URI.create(catalogUri+requestedElement.get()), accessor, depth);

            }
        }
        // This catch should never happen
        catch(IsSelfDescriptionException isDescription){
            return ResponseEntity.ok(brokerSelfDescription.getSelfDescription());
        }
        catch(RejectMessageException e){
            return new ResponseEntity<>(e.getRejectionPayload(), HttpStatus.valueOf(e.getRejectionReason().name()));
        }

        RDFFormat outputFormat=service.returnOutputFormat(headers);
        RDFDataMgr.write(stringWriter, model, outputFormat);

        return ResponseEntity.ok(stringWriter.toString());
    }


    // TODO: Remove Connector:
    /* 1. Not saved
        2. Already Saved
    * */
}
