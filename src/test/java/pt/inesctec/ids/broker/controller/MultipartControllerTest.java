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
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import ids.messaging.core.daps.ConnectorMissingCertExtensionException;
import ids.messaging.core.daps.DapsConnectionException;
import ids.messaging.core.daps.DapsEmptyResponseException;
import ids.messaging.core.daps.DapsTokenProvider;
import ids.messaging.util.IdsMessageUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pt.inesctec.ids.broker.CompareService;
import pt.inesctec.ids.broker.builder.MultipartMessageBuilder;
import pt.inesctec.ids.broker.converter.MultipartConverter;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.dto.ResponseMessage;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.JenaService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.CompareUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.MessageUtil;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static pt.inesctec.ids.broker.config.IDSConstants.*;

// I'm using the test properties to be able to compare the output
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    locations = "classpath:application-test.properties")
class MultipartControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(MultipartControllerTest.class);

    //This makes the code run without having the project running
    @LocalServerPort
    private String port;

    @Value(value="${spring.security.user.name}")
    private String username;

    @Value(value="${spring.security.user.password}")
    private String password;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    DapsTokenProvider provider;

    @Autowired
    FusekiConnectionAccessor accessor;

    @Autowired BrokerSelfDescription brokerSelfDescription;

    BrokerService brokerService;

    Connector connectorProducer, connectorSubcontracted;
    TestUtil testUtil;

    String idsUrl;

    CompareService localhostService;

    @BeforeEach
    void setUp() {
        brokerService=new BrokerService(accessor);
        testUtil=new TestUtil();

        testUtil.removeAllGraphsFromDB(accessor);

        // Prepare environment
        connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
        connectorSubcontracted=testUtil.createConnectorSubcontracted();
        brokerService.addConnectorToFuseki(connectorProducer);
        brokerService.addConnectorToFuseki(connectorSubcontracted);
        brokerService.addResourceToFuseki(testUtil.createResource(), connectorProducer.getId());

        idsUrl="http://localhost:" + port + "/api/ids/data";

        localhostService =new CompareService(idsUrl);

    }

    @Test
    void getPublicSelfDescription() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        logger.info("getPublicSelfDescription()");
        DescriptionRequestMessage descriptionRequestMessage=testUtil.descriptionRequestMessage(provider.getDAT());

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        System.out.println(headers.toString());

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(HEADER, descriptionRequestMessage.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Do the Request
        ResponseEntity<String> response = restTemplate
            .postForEntity(idsUrl, requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        // Convert the Response into a Map to verify its contebt
        MultipartConverter converter=new MultipartConverter();
        Map<String, Object> messageResponse = converter.convert2Map(response.getBody().getBytes());

        Message responseHeader = MessageUtil.deserialize(messageResponse.get(HEADER).toString(), DescriptionResponseMessage.class);
        assertThat(responseHeader.getCorrelationMessage()).isEqualTo(descriptionRequestMessage.getId());
        assertThat(responseHeader.getIssuerConnector().toString()).isEqualTo("https://inesctec.pt/ids/mediator/id/metadata-broker");
        assertThat(responseHeader.getSenderAgent().toString()).isEqualTo("https://inesctec.pt/ids/mediator/id/metadata-broker");

        Broker responsePayload = MessageUtil.deserialize(messageResponse.get(PAYLOAD).toString(), Broker.class);
        InfrastructureComponent expectedSelfDescription=brokerSelfDescription.getSelfDescription();

        // Check that all values are the same (Ignore IDs)
        assertThat(responsePayload.getId()).isEqualTo(expectedSelfDescription.getId());
        assertThat(responsePayload.getVersion()).isEqualTo(expectedSelfDescription.getVersion());
        assertThat(responsePayload.getPublicKey()).isNotNull();
        assertThat(responsePayload.getHasDefaultEndpoint().getAccessURL().toString()).isEqualTo(((Broker)expectedSelfDescription).getHasDefaultEndpoint().getAccessURL().toString());

        assertThat(responsePayload.getHasEndpoint().get(0).getAccessURL().toString()).isEqualTo(((Broker) expectedSelfDescription).getHasEndpoint().get(0).getAccessURL().toString());
        assertThat(responsePayload.getHasEndpoint().get(0).getPath()).isEqualTo(((Broker) expectedSelfDescription).getHasEndpoint().get(0).getPath());
        assertThat(responsePayload.getHasEndpoint().get(0).getEndpointInformation().size()).isEqualTo(2);
        assertThat(CompareUtil.compareListOfTypedLiterals(responsePayload.getHasEndpoint().get(0).getEndpointInformation(),
            ((Broker) expectedSelfDescription).getHasEndpoint().get(0).getEndpointInformation()))
            .isTrue();

        assertThat(responsePayload.getSecurityProfile()).isEqualTo(((Broker) expectedSelfDescription).getSecurityProfile());
        assertThat(responsePayload.getMaintainer()).isEqualTo(expectedSelfDescription.getMaintainer());
        assertThat(responsePayload.getCurator()).isEqualTo(expectedSelfDescription.getCurator());
        assertThat(responsePayload.getResourceCatalog()).isNotNull();
        // Should be equal to the catalogUIR
        assertThat(responsePayload.getResourceCatalog().get(0).getOfferedResource().get(0).getRepresentation().get(0).getInstance().get(0).getId().toString()).isEqualTo(accessor.getCatalogUri());

    }


    @Test
    void getConnectors_Unauthorize()  {
        logger.info("getPublicSelfDescription()");

        // Set an invalid token (outdated) in the correct format
        DescriptionRequestMessage descriptionRequestMessage=new DescriptionRequestMessageBuilder(URI.create("http://description/request/message/id/1a421b8c-3407-44a8-aeb9-253f145c869a"))
            ._securityToken_(new DynamicAttributeTokenBuilder()
                ._tokenFormat_(TokenFormat.JWT)
                ._tokenValue_("eyJ0eXAiOiJhdCtqd3QiLCJraWQiOiI2MGJlZmYxYWQ2NjJlMzhmZDg5OTY2MzkyODZlM2MyNGUyYjM2NmU1MmY4N2Q4ODI1MWI4NTQwOTZlZjc4YzM5IiwiYWxnIjoiUlMyNTYifQ.eyJzY29wZSI6Im9tZWpkbjphZG1pbiIsImF1ZCI6WyJpZHNjOklEU19DT05ORUNUT1JTX0FMTCIsImh0dHBzOi8vdmNlc2UxOS5pbmVzY3RlYy5wdC9hdXRoL2FwaSJdLCJpc3MiOiJodHRwczovL3ZjZXNlMTkuaW5lc2N0ZWMucHQvYXV0aCIsInN1YiI6ImFkbWluIiwibmJmIjoxNzI1ODkyMTE3LCJpYXQiOjE3MjU4OTIxMTcsImp0aSI6IjYxNTdmZjZjLTIxNTgtNDI4OC1hYzMyLTAwZDM3OGJlMGNiMSIsImV4cCI6MTcyNTg5NTcxNywiY2xpZW50X2lkIjoicG9zdG1hbiJ9.JjQkW23GIO1GL8kJUXFBwZU4iT79lvfYBIGuoBdM20ODCplk0S_6YyU5hB_Wv13JpTVlh87xaYKmXF3R25XRWChJzJPvFqbn0egJCKRm0q2CO9xh5G8QNpoi7qm8rle6XvcY3IoWfbQ3f6KcJ6VjQDyy7LLIzN-C4_zV1grgb9LkclgzXFpwDfCFXFd3WiCQH95SN12kf3EQo2DnAUaC6KQxBQL4bOqOwphdnCi5OuYkkul-fBA--A4rY9W1_o73z0rkbY0-jBw-mSMOnkyPIHnS9U5FTCvwsUEeQSdIvTHgcnseMUIE7CcQmROmGSqICCVNhA2WOt65I246mJhgqw")
                .build())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(URI.create("https://issuerConnector"))
            ._modelVersion_("4.0.2")
            ._senderAgent_(URI.create("https://senderAgent"))
            .build();

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        System.out.println(headers.toString());

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(HEADER, descriptionRequestMessage.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Do the Request
        ResponseEntity<String> response = restTemplate
            .postForEntity(idsUrl, requestEntity, String.class);


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        System.out.println(response.getBody());

        // Convert the Response into a Map to verify its contebt
        MultipartConverter converter=new MultipartConverter();
        Map<String, Object> messageResponse = converter.convert2Map(response.getBody());

        // Check the Header
        Message responseHeader = MessageUtil.deserialize(messageResponse.get(HEADER).toString(), RejectionMessage.class);
        assertThat(((RejectionMessage)responseHeader).getRejectionReason()).isEqualTo(RejectionReason.NOT_AUTHENTICATED);
        assertThat(messageResponse.get(PAYLOAD)).isEqualTo("Error processing token.");

    }

    @Test
    void getConnectors_Unauthorize_InvalidTokenFormat()  {
        logger.info("getPublicSelfDescription()");

        // Set an invalid token (incorrect format)
        DescriptionRequestMessage descriptionRequestMessage=new DescriptionRequestMessageBuilder(URI.create("http://description/request/message/id/1a421b8c-3407-44a8-aeb9-253f145c869a"))
            ._securityToken_(new DynamicAttributeTokenBuilder()
                ._tokenFormat_(TokenFormat.JWT)
                ._tokenValue_("Invalid Token")
                .build())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(URI.create("https://issuerConnector"))
            ._modelVersion_("4.0.2")
            ._senderAgent_(URI.create("https://senderAgent"))
            .build();

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        System.out.println(headers.toString());

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(HEADER, descriptionRequestMessage.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Do the Request
        ResponseEntity<String> response = restTemplate
            .postForEntity(idsUrl, requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        System.out.println(response.getBody());

        // Convert the Response into a Map to verify its contebt
        MultipartConverter converter=new MultipartConverter();
        Map<String, Object> messageResponse = converter.convert2Map(response.getBody());

        // Check the Header
        Message responseHeader = MessageUtil.deserialize(messageResponse.get(HEADER).toString(), RejectionMessage.class);
        assertThat(((RejectionMessage)responseHeader).getRejectionReason()).isEqualTo(RejectionReason.NOT_AUTHENTICATED);
        assertThat(messageResponse.get(PAYLOAD)).isEqualTo("Error verifying token.");

    }

    @Test
    void getConnectors_InvalidHeader() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        logger.info("getPublicSelfDescription()");

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        System.out.println(headers.toString());

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Doing it like this allows for using an invalid format Message (e.g., response message whithout the correlation message field). Otherwise, the error is thrown when building it
        body.add(HEADER, "{\n" +
            "  \"@context\" : {\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\",\n" +
            "    \"idsc\" : \"https://w3id.org/idsa/code/\",\n" +
            "    \"xsd\":\"http://www.w3.org/2001/XMLSchema#\"\n" +
            "  },\n" +
            "  \"@type\" : \"ids:DescriptionResponseMessage\",\n" +
            "  \"@id\" : \"http://industrialdataspace.org/1a421b8c-3407-44a8-aeb9-253f145c869a\",\n" +
            "  \"ids:issued\" : {\"@value\":\"2021-05-25T15:35:34.589Z\",\"@type\":\"xsd:dateTimeStamp\"},\n" +
            "  \"ids:modelVersion\" : \"4.0.0\",\n" +
            "  \"ids:senderAgent\":{\"@id\":\"https://localhost/agent\"},\n" +
            "  \"ids:issuerConnector\":{\"@id\":\"https://localhost/59a68243\"},\n" +
            "  \"ids:securityToken\" : {\n" +
            "    \"@type\" : \"ids:DynamicAttributeToken\",\n" +
            "    \"@id\" : \"https://w3id.org/idsa/autogen/dynamicAttributeToken/2bd53efc-5995-d75590476820\",\n" +
            "    \"ids:tokenFormat\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/code/JWT\"\n" +
            "    },\n" +
            "    \"ids:tokenValue\" : "+ provider.getDAT()+"\n" +
            "  }\n" +
            "}");

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Do the Request
        ResponseEntity<String> response = restTemplate
            .postForEntity(idsUrl, requestEntity, String.class);


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        System.out.println(response.getBody());



        // Convert the Response into a Map to verify its contebt
        MultipartConverter converter=new MultipartConverter();
        Map<String, Object> messageResponse = converter.convert2Map(response.getBody().getBytes());

        // Check the Header
        Message responseHeader = MessageUtil.deserialize(messageResponse.get(HEADER).toString(), RejectionMessage.class);
        assertThat(responseHeader.getCorrelationMessage().toString()).isEqualTo("http://industrialdataspace.org/1a421b8c-3407-44a8-aeb9-253f145c869a");
        assertThat(((RejectionMessage)responseHeader).getRejectionReason()).isEqualTo(RejectionReason.MALFORMED_MESSAGE);

        // Check the Payload
        assertThat(messageResponse.get(PAYLOAD)).isEqualTo("Could not parse incoming message!");

    }

    /**
     * Tests sending a message type that I do not have any Handlers
     * */
    @Test
    void messageTypeNotSupported() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        logger.info("getPublicSelfDescription()");

        DescriptionResponseMessage descriptionResponseMessage=new DescriptionResponseMessageBuilder(URI.create("http://description/request/message/id/1a421b8c-3407-44a8-aeb9-253f145c869a"))
            ._securityToken_(provider.getDAT())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(URI.create("https://issuerConnector"))
            ._modelVersion_("4.0.2")
            ._senderAgent_(URI.create("https://senderAgent"))
            ._correlationMessage_(URI.create("https://correlationMessage"))
            .build();

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        System.out.println(headers.toString());

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(HEADER, descriptionResponseMessage.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Do the Request
        ResponseEntity<String> response = restTemplate
            .postForEntity(idsUrl, requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        System.out.println(response.getBody());



        // Convert the Response into a Map to verify its contebt
        MultipartConverter converter=new MultipartConverter();
        Map<String, Object> messageResponse = converter.convert2Map(response.getBody().getBytes());

        // Check the Header
        Message responseHeader = MessageUtil.deserialize(messageResponse.get(HEADER).toString(), RejectionMessage.class);
        assertThat(((RejectionMessage)responseHeader).getRejectionReason()).isEqualTo(RejectionReason.MESSAGE_TYPE_NOT_SUPPORTED);

        // Check the Payload
        assertThat(messageResponse.get(PAYLOAD)).isEqualTo("No handler for provided message type was found!");

    }


    @Test
    void description_InvalidJson() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        // Set an invalid token
        String invalidIDSMessage="{\n" +
            "  \"@context\" : {\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\",\n" +
            "    \"idsc\" : \"https://w3id.org/idsa/code/\",\n" +
            "    \"xsd\":\"http://www.w3.org/2001/XMLSchema#\"\n" +
            "  },\n" +
            "  \"@type\" : \"ids:DescriptionResponseMessage\",\n" +
            "  \"@id\" : \"http://industrialdataspace.org/1a421b8c-3407-44a8-aeb9-253f145c869a\",\n" +
            "  \"ids:issued\" : {\"@value\":\"2021-05-25T15:35:34.589Z\",\"@type\":\"xsd:dateTimeStamp\"},\n" +
            "  \"ids:modelVersion\" : \"4.0.0\",\n" +
            "  \"ids:senderAgent\":{\"@id\":\"https://localhost/agent\"},\n" +
            "  \"ids:issuerConnector\":{\"@id\":\"https://localhost/59a68243\"},\n" +
            "  \"ids:securityToken\" : {\n" +
            "    \"@type\" : \"ids:DynamicAttributeToken\",\n" +
            "    \"@id\" : \"https://w3id.org/idsa/autogen/dynamicAttributeToken/2bd53efc-5995-d75590476820\",\n" +
            "    \"ids:tokenFormat\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/code/JWT\"\n" +
            "    },\n" +
            "    \"ids:tokenValue\" : "+ provider.getDAT()+"\n" +
            "  }\n" +
            "}";

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);


        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(HEADER, invalidIDSMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        
        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.postForEntity(idsUrl, requestEntity, String.class);

        // Test the Response Status Code
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        System.out.println("INESCTEC: " + response_INESCTEC.toString());

        // Convert the Response into a Map to verify its contebt
        MultipartConverter converter=new MultipartConverter();
        Map<String, Object> messageResponse_INESCTEC = converter.convert2Map(response_INESCTEC.getBody());

        // Check the Header
        Message responseHeader_INESCTEC = MessageUtil.deserialize(messageResponse_INESCTEC.get(HEADER).toString(), RejectionMessage.class);

        // Check the Headers
        assertThat(((RejectionMessage)responseHeader_INESCTEC).getRejectionReason()).isEqualTo(RejectionReason.MALFORMED_MESSAGE);

        assertThat(responseHeader_INESCTEC.getCorrelationMessage().toString()).isEqualTo("http://industrialdataspace.org/1a421b8c-3407-44a8-aeb9-253f145c869a");

        assertThat(responseHeader_INESCTEC.getSecurityToken().getTokenValue()).doesNotContain("INVALID");


        // Test the Payload
        assertThat(messageResponse_INESCTEC.get(PAYLOAD)).isEqualTo("Could not parse incoming message!");
    }

    @Test
    void description() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        DescriptionRequestMessage descriptionRequestMessage=testUtil.descriptionRequestMessage(provider.getDAT());


        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.MULTIPART_MIXED));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);


        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(descriptionRequestMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);


        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        DocumentContext ctx_INESCTEC = JsonPath.parse(localhost_response_map.get(HEADER).toString());

        // If I retrieve the Header this way, it loses the 'Serialization' and 'elementType' properties
        /*Message responseHeader_IDSA = MessageUtil.deserialize(messageResponse_IDSA.get(HEADER).toString(), DescriptionResponseMessage.class);
        Message responseHeader_INESCTEC = MessageUtil.deserialize(messageResponse_INESCTEC.get(HEADER).toString(), DescriptionResponseMessage.class);*/

        // Asssert values
        assertThat(URI.create(ctx_INESCTEC.read("$['ids:correlationMessage']['@id']", String.class))).isEqualTo(descriptionRequestMessage.getId());

        assertThat(ctx_INESCTEC.read("$['Serialization']", String.class)).contains("JSON-LD");

        assertThat(ctx_INESCTEC.read("$['elementType']", String.class)).isEqualTo("BrokerImpl");

        // Test the Payload
        Broker responsePayload_INESCTEC = MessageUtil.deserialize(localhost_response_map.get(PAYLOAD).toString(), Broker.class);

        assertThat(responsePayload_INESCTEC.getResourceCatalog()).isNotNull();
        assertThat(responsePayload_INESCTEC.getHasEndpoint().get(0).getEndpointInformation().size()).isEqualTo(2);
        assertThat(responsePayload_INESCTEC.getHasEndpoint().get(0).getPath()).isEqualTo("/api/ids/data");
        assertThat(responsePayload_INESCTEC.getSecurityProfile()).isEqualTo(SecurityProfile.BASE_SECURITY_PROFILE);

    }

    @Test
    @Order(1)
    void connector_registration_no_resource() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToInsert=testUtil.createConnector();
        ConnectorUpdateMessage connectorUpdateMessage=testUtil.connectorUpdateMessage(provider.getDAT(), connectorToInsert);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);


        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(connectorUpdateMessage);
        body.add(PAYLOAD, connectorToInsert.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Request the IDSA Broker

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header - If using the Serializer, it loses the 'Location' property
        DocumentContext ctx_INESCTEC = getHeader(localhost_response_map);

        // Asssert values
        assertThat(ctx_INESCTEC.read("$['@type']", String.class)).isEqualTo("ids:MessageProcessedNotificationMessage");
        assertThat(URI.create(ctx_INESCTEC.read("$['ids:correlationMessage']['@id']", String.class))).isEqualTo(connectorUpdateMessage.getId());
        assertThat(ctx_INESCTEC.read("$['Location']", String.class)).isEqualTo("<http://test-hostname:8082/connectors/connector-producer/-846787637>");
        assertThat(localhost_response_map.get(PAYLOAD)).isNotNull();

        DocumentContext ctx_INESCTEC_payload = getPayload(localhost_response_map);
        assertThat(ctx_INESCTEC_payload.read("$['@type']", String.class)).isEqualTo("ids:BaseConnector");

    }

    @Test
    @Order(2)
    void resource_registration() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToInsert=testUtil.createConnector();
        Resource resourceToInsert=testUtil.createResourceWithMultipleArtefacts();
        ResourceUpdateMessage resourceUpdateMessage=testUtil.resourceUpdateMessage(connectorToInsert, resourceToInsert, provider.getDAT());

        // Add Connector to Databse
        brokerService.addConnectorToFuseki(connectorToInsert);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);


        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(resourceUpdateMessage);
        body.add(PAYLOAD, resourceToInsert.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header - If using th eSerializer, it loses the 'Location' property
        DocumentContext ctx_INESCTEC = getHeader(localhost_response_map);

        // Assert values
        assertThat(ctx_INESCTEC.read("$['@type']", String.class)).isEqualTo("ids:MessageProcessedNotificationMessage");
        assertThat(URI.create(ctx_INESCTEC.read("$['ids:correlationMessage']['@id']", String.class))).isEqualTo(resourceUpdateMessage.getId());
        assertThat(ctx_INESCTEC.read("$['Location']", String.class)).isEqualTo("<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>");
        assertThat(localhost_response_map.get(PAYLOAD)).isNotNull();

        DocumentContext ctx_INESCTEC_payload = getPayload(localhost_response_map);
        assertThat(ctx_INESCTEC_payload.read("$['@type']", String.class)).isEqualTo("ids:Resource");

    }

    @Test
    @Order(3)
    void resource_unregistration() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToInsert=testUtil.createConnector();
        Resource resourceToInsert=testUtil.createResourceWithMultipleArtefacts();
        ResourceUnavailableMessage resourceUnavailableMessage=testUtil.resourceUnavailableMessage(connectorToInsert, resourceToInsert, provider.getDAT());

        // Add Connector to Databse
        brokerService.addConnectorToFuseki(connectorToInsert);
        brokerService.addResourceToFuseki(resourceToInsert, connectorToInsert.getId());

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(resourceUnavailableMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        logger.info("Executing test ...");
        // Request the IDSA Broker

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        MessageProcessedNotificationMessage inesctecMessageHeader = MessageUtil.deserialize(localhostService.extractHeaderFromResponse(inesctec_post_response).toString(), MessageProcessedNotificationMessage.class);
        assertThat(inesctecMessageHeader.getCorrelationMessage()).isEqualTo(resourceUnavailableMessage.getId());


        // Check the Payload
        assertThat(localhost_response_map.get(PAYLOAD).toString()).isEqualTo("");


    }

    @Test
    @Order(4)
    void resource_unregistration_already_unregistered() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToInsert=testUtil.createConnector();
        Resource resourceToInsert=testUtil.createResourceWithMultipleArtefacts();
        ResourceUnavailableMessage resourceUnavailableMessage=testUtil.resourceUnavailableMessage(connectorToInsert, resourceToInsert, provider.getDAT());

        // Add Connector to Databse -> Do not add Resource
        brokerService.addConnectorToFuseki(connectorToInsert);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(resourceUnavailableMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        logger.info("Executing test ...");
        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        RejectionMessage inesctecMessageHeader = MessageUtil.deserialize(localhostService.extractHeaderFromResponse(inesctec_post_response).toString(), RejectionMessage.class);

        assertThat(inesctecMessageHeader.getCorrelationMessage()).isEqualTo(resourceUnavailableMessage.getId());
        assertThat(inesctecMessageHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);


        // Check the Payload
        assertThat(localhost_response_map.get(PAYLOAD).toString()).isEqualTo("Resource not found with ID : 'https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5'. Did you send a ResourceUpdateMessage yet?");
    }

    @Test
    @Order(5)
    void connector_unregistration_no_resource() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToRemove=testUtil.createConnector();
        ConnectorUnavailableMessage connectorUnavailableMessage=testUtil.ConnectorUnavailableMessage(provider.getDAT(), connectorToRemove);

        // Add Connector to Databse
        brokerService.addConnectorToFuseki(connectorToRemove);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(connectorUnavailableMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        logger.info("Executing test ...");
        // Request the IDSA Broker

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        MessageProcessedNotificationMessage inesctecMessageHeader = MessageUtil.deserialize(localhostService.extractHeaderFromResponse(inesctec_post_response).toString(), MessageProcessedNotificationMessage.class);

        assertThat(inesctecMessageHeader.getCorrelationMessage()).isEqualTo(connectorUnavailableMessage.getId());


        // Check the Payload
        assertThat(localhost_response_map.get(PAYLOAD).toString()).isEqualTo("");


    }

    @Test
    @Order(6)
    void connector_unregistration_already_removed() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToRemove=testUtil.createConnector();
        ConnectorUnavailableMessage connectorUnavailableMessage=testUtil.ConnectorUnavailableMessage(provider.getDAT(), connectorToRemove);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(connectorUnavailableMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        // TODO: Maybe change to NOT_FOUND??
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        RejectionMessage inesctecMessageHeader = MessageUtil.deserialize(localhostService.extractHeaderFromResponse(inesctec_post_response).toString(), RejectionMessage.class);

        assertThat(inesctecMessageHeader.getCorrelationMessage()).isEqualTo(connectorUnavailableMessage.getId());
        assertThat(inesctecMessageHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);

        // Check the Payload
        assertThat(localhost_response_map.get(PAYLOAD).toString()).isEqualTo("Connector not found with ID 'https://test-broker.com/id/test/connector-producer'. Did you send a ConnectorUpdateMessage yet?");
    }

    @Test
    @Order(7)
    void resource_unregistration_connector_is_already_unregistered() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToInsert=testUtil.createConnector();
        Resource resourceToInsert=testUtil.createResourceWithMultipleArtefacts();
        ResourceUnavailableMessage resourceUnavailableMessage=testUtil.resourceUnavailableMessage(connectorToInsert, resourceToInsert, provider.getDAT());

        // Add Connector to Databse -> Do not add Resource
        //brokerService.addConnectorToFuseki(connectorToInsert);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(resourceUnavailableMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        logger.info("Executing test ...");

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        RejectionMessage inesctecMessageHeader = MessageUtil.deserialize(localhostService.extractHeaderFromResponse(inesctec_post_response).toString(), RejectionMessage.class);

        assertThat(inesctecMessageHeader.getCorrelationMessage()).isEqualTo(resourceUnavailableMessage.getId());
        assertThat(inesctecMessageHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);

        // Check the Payload
        assertThat(localhost_response_map.get(PAYLOAD).toString()).isEqualTo("Connector not found with ID : 'https://test-broker.com/id/test/connector-producer'");


    }


    @Test
    @Order(8)  // It Fails on Postman because of the token_producer variable
    void connector_registration_with_resource() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToInsert=testUtil.createConnector();
        Resource resourceToInsert=testUtil.createResourceWithMultipleArtefacts();
        connectorToInsert.setResourceCatalog(List.of(new ResourceCatalogBuilder()._offeredResource_(resourceToInsert).build()));
        ConnectorUpdateMessage connectorUpdateMessage=testUtil.connectorUpdateMessage(provider.getDAT(), connectorToInsert);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);


        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(connectorUpdateMessage);
        body.add(PAYLOAD, connectorToInsert.toRdf());

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header - If using the Serializer, it loses the 'Location' property
        DocumentContext ctx_INESCTEC = getHeader(localhost_response_map);

        // Asssert values
        assertThat(ctx_INESCTEC.read("$['@type']", String.class)).isEqualTo("ids:MessageProcessedNotificationMessage");
        assertThat(URI.create(ctx_INESCTEC.read("$['ids:correlationMessage']['@id']", String.class))).isEqualTo(connectorUpdateMessage.getId());
        assertThat(ctx_INESCTEC.read("$['Location']", String.class)).isEqualTo("<http://test-hostname:8082/connectors/connector-producer/-846787637>");
        assertThat(localhost_response_map.get(PAYLOAD)).isNotNull();

        DocumentContext ctx_INESCTEC_payload = getPayload(localhost_response_map);
        assertThat(ctx_INESCTEC_payload.read("$['@type']", String.class)).isEqualTo("ids:BaseConnector");

    }

    @Test
    @Order(9)
    void connector_unregistration_with_resource() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        Connector connectorToRemove=testUtil.createConnector();
        Resource resourceToInsert=testUtil.createResourceWithMultipleArtefacts();
        connectorToRemove.setResourceCatalog(List.of(new ResourceCatalogBuilder()._offeredResource_(resourceToInsert).build()));
        ConnectorUnavailableMessage connectorUnavailableMessage=testUtil.ConnectorUnavailableMessage(provider.getDAT(), connectorToRemove);

        // Add Connector to Databse
        brokerService.addConnectorToFuseki(connectorToRemove);

        // Create list with all Accepted Headers
        HttpHeaders headers = addHeaders(provider);

        // Add the Message to the Body parameters
        MultiValueMap<String, Object> body = addHeaderToBody(connectorUnavailableMessage);

        // Build a HttpEntity to send on the Request (Builds the Multipart)
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        logger.info("Executing test ...");
        // Request the INESCTEC Broker
        ResponseEntity<String>  inesctec_post_response=localhostService.executePOSTRequest(requestEntity);

        // Test the Response Status Code
        assertThat(inesctec_post_response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> localhost_response_map = localhostService.convert2Map(inesctec_post_response);

        // Check the Header
        MessageProcessedNotificationMessage inesctecMessageHeader = MessageUtil.deserialize(localhostService.extractHeaderFromResponse(inesctec_post_response).toString(), MessageProcessedNotificationMessage.class);

        assertThat(inesctecMessageHeader.getCorrelationMessage()).isEqualTo(connectorUnavailableMessage.getId());


        // Check the Payload
        assertThat(localhost_response_map.get(PAYLOAD).toString()).isEqualTo("");

    }


    private static HttpHeaders addHeaders(DapsTokenProvider provider) throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(provider.getDAT().getTokenValue());
        headers.setAccept(List.of(MediaType.ALL));
        return headers;
    }

    private static MultiValueMap<String, Object> addHeaderToBody(Message idsMessage) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(HEADER, idsMessage.toRdf());
        return body;
    }

    private static DocumentContext getHeader(Map<String, Object> map) {
        return JsonPath.parse(map.get(HEADER).toString());
    }

    private static DocumentContext getPayload(Map<String, Object> map) {
        return JsonPath.parse(map.get(PAYLOAD).toString());
    }

}