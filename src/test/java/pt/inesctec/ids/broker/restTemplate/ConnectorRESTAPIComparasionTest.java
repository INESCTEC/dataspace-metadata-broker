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
package pt.inesctec.ids.broker.restTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import ids.messaging.core.daps.ConnectorMissingCertExtensionException;
import ids.messaging.core.daps.DapsConnectionException;
import ids.messaging.core.daps.DapsEmptyResponseException;
import ids.messaging.core.daps.DapsTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Compares the output of theIDSA Broker with the same call on this Broker
 * */
// I'm using the test properties to be able to compare the output
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    locations = "classpath:application-test.properties")
class ConnectorRESTAPIComparasionTest {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ConnectorRESTAPIComparasionTest.class);
    private final ObjectMapper objectMapper = new ObjectMapper();


    private static final MediaType TEXT_TURTLE = new MediaType("text", "turtle");
    private static final MediaType RDF_XML = new MediaType("application", "rdf+xml");
    private static final MediaType NTRIPLES = new MediaType("application", "n-triples");
    private static final MediaType JSON_LD = new MediaType("application", "ld+json");

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

    BrokerService brokerService;

    Connector connectorProducer, connectorSubcontracted;
    TestUtil testUtil;
    Serializer serializer=new Serializer();

    private String brokerIDSAPath="https://vcese19.inesctec.pt:4567/connectors/";
    private String brokerLocalhostPath;//="https://vcese19.inesctec.pt:4568/test/connectors";

    @BeforeEach
    void setUp() {
        brokerLocalhostPath="http://localhost:" + port + "/connectors";

        brokerService=new BrokerService(accessor);
        testUtil=new TestUtil();

        testUtil.removeAllGraphsFromDB(accessor);

        // Prepare environment
        connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
        connectorSubcontracted=testUtil.createConnectorSubcontracted();
        brokerService.addConnectorToFuseki(connectorProducer);
        brokerService.addConnectorToFuseki(connectorSubcontracted);
        brokerService.addResourceToFuseki(testUtil.createResource(), connectorProducer.getId());


    }


    @Test
    void GETConnectors_Unauthorize()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        //headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // Override the ids-securitytoken to set an invalid token in the correct format
        headers.set("ids-securitytoken", "eyJ0eXAiOiJhdCtqd3QiLCJraWQiOiI2MGJlZmYxYWQ2NjJlMzhmZDg5OTY2MzkyODZlM2MyNGUyYjM2NmU1MmY4N2Q4ODI1MWI4NTQwOTZlZjc4YzM5IiwiYWxnIjoiUlMyNTYifQ.eyJzY29wZSI6Im9tZWpkbjphZG1pbiIsImF1ZCI6WyJpZHNjOklEU19DT05ORUNUT1JTX0FMTCIsImh0dHBzOi8vdmNlc2UxOS5pbmVzY3RlYy5wdC9hdXRoL2FwaSJdLCJpc3MiOiJodHRwczovL3ZjZXNlMTkuaW5lc2N0ZWMucHQvYXV0aCIsInN1YiI6ImFkbWluIiwibmJmIjoxNzI1ODkyMTE3LCJpYXQiOjE3MjU4OTIxMTcsImp0aSI6IjYxNTdmZjZjLTIxNTgtNDI4OC1hYzMyLTAwZDM3OGJlMGNiMSIsImV4cCI6MTcyNTg5NTcxNywiY2xpZW50X2lkIjoicG9zdG1hbiJ9.JjQkW23GIO1GL8kJUXFBwZU4iT79lvfYBIGuoBdM20ODCplk0S_6YyU5hB_Wv13JpTVlh87xaYKmXF3R25XRWChJzJPvFqbn0egJCKRm0q2CO9xh5G8QNpoi7qm8rle6XvcY3IoWfbQ3f6KcJ6VjQDyy7LLIzN-C4_zV1grgb9LkclgzXFpwDfCFXFd3WiCQH95SN12kf3EQo2DnAUaC6KQxBQL4bOqOwphdnCi5OuYkkul-fBA--A4rY9W1_o73z0rkbY0-jBw-mSMOnkyPIHnS9U5FTCvwsUEeQSdIvTHgcnseMUIE7CcQmROmGSqICCVNhA2WOt65I246mJhgqw");

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Test the Response Body
        assertThat(response_IDSA.getBody()).isEqualTo("Error processing token.");
        assertThat(response_INESCTEC.getBody()).isEqualTo("Error processing token.");

        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        //response_IDSA.getHeaders().toSingleValueMap().forEach(System.out::printf);
        System.err.println(response_IDSA.getHeaders().toSingleValueMap().toString());


        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! k.contains("ids"));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! k.contains("ids"));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(String.valueOf(RejectionReason.NOT_AUTHENTICATED));
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(String.valueOf(RejectionReason.NOT_AUTHENTICATED));
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());
    }

    @Test
    void GETConnectors_Unauthorize_InvalidTokenFormat()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        //headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // Override the ids-securitytoken to set an invalid toknen
        headers.set("ids-securitytoken", "Invalid Token");


        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Test the Response Body
        assertThat(response_IDSA.getBody()).isEqualTo("Error verifying token.");
        assertThat(response_INESCTEC.getBody()).isEqualTo("Error verifying token.");

        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        //response_IDSA.getHeaders().toSingleValueMap().forEach(System.out::printf);
        System.err.println(response_IDSA.getHeaders().toSingleValueMap().toString());


        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! k.contains("ids"));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! k.contains("ids"));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(String.valueOf(RejectionReason.NOT_AUTHENTICATED));
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(String.valueOf(RejectionReason.NOT_AUTHENTICATED));
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());

    }

    @Test
    void GETConnectors_ReturnMissingHeader()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        addIDSHeaders(headers, provider);

        logger.info("First test");
        // Remove Header
        headers.remove("ids-securitytoken");

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        // Print
        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_IDSA.getBody()).isEqualTo("Missing header: ids-securitytoken");

        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_INESCTEC.getBody()).isEqualTo("Missing header: ids-securitytoken");

        // Clear Environment
        headers.clear();

        logger.info("Second test");
        addIDSHeaders(headers, provider);

        // Remove Header
        headers.remove("ids-modelversion");

        // Prepare the headers to the Request
        request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        // Print
        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_IDSA.getBody()).isEqualTo("Missing header: ids-modelversion");

        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_INESCTEC.getBody()).isEqualTo("Missing header: ids-modelversion");

        // Clear Environment
        headers.clear();

        logger.info("Third test");
        addIDSHeaders(headers, provider);

        // Remove Header
        headers.remove("ids-issued");

        // Prepare the headers to the Request
        request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        // Print
        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_IDSA.getBody()).isEqualTo("Missing header: ids-issued");

        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_INESCTEC.getBody()).isEqualTo("Missing header: ids-issued");

        // Clear Environment
        headers.clear();

        logger.info("Fourth test");
        addIDSHeaders(headers, provider);

        // Remove Header
        headers.remove("ids-senderagent");

        // Prepare the headers to the Request
        request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        // Print
        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_IDSA.getBody()).isEqualTo("Missing header: ids-senderagent");

        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_INESCTEC.getBody()).isEqualTo("Missing header: ids-senderagent");

        // Clear Environment
        headers.clear();

        logger.info("Fifth test");
        addIDSHeaders(headers, provider);

        // Remove Header
        headers.remove("ids-issuerconnector");

        // Prepare the headers to the Request
        request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        // Print
        System.out.println("Headers IDSA: " + response_IDSA.toString());
        System.out.println("Headers INESCTEC: " + response_INESCTEC.toString());

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_IDSA.getBody()).isEqualTo("Missing header: ids-issuerconnector");

        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response_INESCTEC.getBody()).isEqualTo("Missing header: ids-issuerconnector");


        // Clear Environment
        headers.clear();

        logger.info("Sixth test");
        addIDSHeaders(headers, provider);

        // Remove Header
        headers.remove("ids-recipientConnector");

        // Prepare the headers to the Request
        request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Clear Environment
        headers.clear();

        logger.info("Seventh test");
        addIDSHeaders(headers, provider);

        // Remove Header
        headers.remove("ids-recipientAgent");

        // Prepare the headers to the Request
        request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);
        // Request the INESCTEC Broker
        response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void GETConnectors_BaseElement_Depth10() throws IOException {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.err.println("INESCTEC Response: " + response_INESCTEC.toString());

        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test the Response Body
        DocumentContext ctx_IDSA = JsonPath.parse(response_IDSA.getBody().toString());
        DocumentContext ctx_INESCTEC = JsonPath.parse(response_INESCTEC.getBody().toString());

        ConnectorCatalog catalogIDSA=serializer.deserialize(response_IDSA.getBody(), ConnectorCatalog.class);
        ConnectorCatalog catalogINESCTEC=serializer.deserialize(response_INESCTEC.getBody(), ConnectorCatalog.class);
        // TODO: 11-10: ficou diferente. Deveria ser  ConnectorCatalog
        // DIFF
        assertThat( ctx_IDSA.read("$.['@graph'][0]['@type']", String.class)).isEqualTo("ids:ConnectorEndpoint");
        //assertThat( ctx_IDSA.read("$.['@graph'][?(@['@type'])]", String.class)).isEqualTo("ids:ConnectorCatalog");
        assertThat( ctx_INESCTEC.read("$.['@graph'][0]['@type']", String.class)).isEqualTo("ids:ConnectorCatalog");



        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/Catalog>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload

        // Assert that the list of ListedOnnectors is not null
        assertThat(catalogIDSA.getListedConnector()).isNotNull();
        assertThat(catalogINESCTEC.getListedConnector()).isNotNull();

        // Assert that the Graph ID is the catalogURI
        //assertThat(ctx_IDSA.read("$.@graph[?(@['@type'] == 'ids:ConnectorCatalog')]['@id']", String.class).isEqualTo(brokerIDSAPath);
        assertThat(catalogIDSA.getId().toString()).isEqualTo(brokerIDSAPath);
        assertThat(catalogINESCTEC.getId().toString()).isEqualTo(accessor.getCatalogUri());

        // Assert that there are SameAs
        assertThat(catalogIDSA.toRdf()).contains("http://www.w3.org/2002/07/owl#sameAs");
        assertThat(catalogINESCTEC.toRdf()).contains("http://www.w3.org/2002/07/owl#sameAs");

        // Assert that the response body contains PublicKeys (for instance)
        System.err.println(response_IDSA.getBody());

        assertThat(response_IDSA.getBody().toString()).contains("ids:PublicKey");
        assertThat(response_INESCTEC.getBody().toString()).contains("ids:PublicKey");
    }

    @Test
    void GETConnectors_BaseElement_NoDepth() throws IOException {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        addIDSHeaders(headers, provider);

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath, HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath, HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test the Response Body
        ConnectorCatalog catalogIDSA=serializer.deserialize(response_IDSA.getBody(), ConnectorCatalog.class);
        ConnectorCatalog catalogINESCTEC=serializer.deserialize(response_INESCTEC.getBody(), ConnectorCatalog.class);
        assertThat(catalogIDSA).isInstanceOf(ConnectorCatalog.class);
        assertThat(catalogINESCTEC).isInstanceOf(ConnectorCatalog.class);

        System.out.println("Headers IDSA: " + response_IDSA.getHeaders());
        System.err.println("Headers INESCTEC: " + response_INESCTEC.getHeaders());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/Catalog>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload

        // Assert that the list of ListedOnnectors is not null
        assertThat(catalogIDSA.getListedConnector()).isNotNull();
        assertThat(catalogINESCTEC.getListedConnector()).isNotNull();

        // Assert that the Graph ID is the catalogURI
        assertThat(catalogIDSA.getId().toString()).isEqualTo(brokerIDSAPath);
        assertThat(catalogINESCTEC.getId().toString()).isEqualTo(accessor.getCatalogUri());

        // Assert that there are SameAs
        assertThat(catalogIDSA.toRdf()).contains("http://www.w3.org/2002/07/owl#sameAs");
        assertThat(catalogINESCTEC.toRdf()).contains("http://www.w3.org/2002/07/owl#sameAs");

        // Assert that the response body does not contain PublicKeys (for instance)
        assertThat(response_IDSA.getBody()).doesNotContain("ids:PublicKey");
        assertThat(response_INESCTEC.getBody()).doesNotContain("ids:PublicKey");
    }

    @Test
    void GETConnectors_WithElementID_InvalidElement()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        addIDSHeaders(headers, provider);

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath + "sub/-821347715", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/sub/-821347715", HttpMethod.GET, request, String.class);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Test the Response Body
        assertThat(response_IDSA.getBody()).isEqualTo("The requested resource was not found");
        assertThat(response_INESCTEC.getBody()).isEqualTo("The requested resource was not found");

        //response_IDSA.getHeaders().toSingleValueMap().forEach(System.out::printf);
        System.err.println(response_IDSA.getHeaders().toSingleValueMap().toString());


        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! k.contains("ids"));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! k.contains("ids"));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(String.valueOf(RejectionReason.NOT_FOUND));
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(String.valueOf(RejectionReason.NOT_FOUND));
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());
    }


    @Test
    void GETConnectors_WithElementID_With_Header_Default() throws IOException {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(JSON_LD, RDF_XML, NTRIPLES));
        addIDSHeaders(headers, provider);

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1831114251", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-subcontracted/-821347780", HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test the Response Body
        Connector connIDSA=serializer.deserialize(response_IDSA.getBody(), Connector.class);
        Connector connINESCTEC=serializer.deserialize(response_INESCTEC.getBody(), Connector.class);
        assertThat(connIDSA).isInstanceOf(Connector.class);
        assertThat(connINESCTEC).isInstanceOf(Connector.class);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/BaseConnector>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/BaseConnector");

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload

        // Assert that the Graph ID is the catalogURI
        assertThat(connIDSA.getId().toString()).isEqualTo(brokerIDSAPath+"-1831114251");
        assertThat(connINESCTEC.getId().toString()).isEqualTo(accessor.getCatalogUri() + "connector-subcontracted/-821347780");

        assertThat(connIDSA.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();
        assertThat(connINESCTEC.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();

    }

    @Test
    void GETConnectors_WithElementID_With_Header_1stPriority_TURTLE() throws IOException {

        HttpHeaders headers = new HttpHeaders();
        // Create list with all Accepted Headers  - Priority is TEXT_TURTLE (However, JSON should be used)
        headers.setAccept(List.of(TEXT_TURTLE, JSON_LD, RDF_XML, NTRIPLES));
        addIDSHeaders(headers, provider);

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1831114251", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-subcontracted/-821347780", HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());

        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/BaseConnector>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/BaseConnector");


        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload
        assertThat(response_IDSA.getBody()).isEqualTo("@prefix ids: <https://w3id.org/idsa/core/> .\n" +
            "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
            "\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251>\n" +
            "        a                         ids:BaseConnector ;\n" +
            "        owl:sameAs                <https://confacts.com/ids/connector/id/subcontracted> ;\n" +
            "        ids:curator               <https://www.inesctec.pt/> ;\n" +
            "        ids:description           \"IDS Connector reference implementation\" ;\n" +
            "        ids:hasDefaultEndpoint    <https://w3id.org/idsa/autogen/connectorEndpoint/39a1f291-4d95-42f0-b853-4e13c56fa925> ;\n" +
            "        ids:inboundModelVersion   \"4.2.2\" , \"4.2.1\" , \"4.1.0\" , \"4.2.4\" , \"4.1.2\" , \"4.2.6\" , \"4.2.7\" , \"4.2.5\" , \"4.0.0\" , \"4.2.3\" , \"4.2.0\" ;\n" +
            "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
            "        ids:outboundModelVersion  \"4.2.7\" ;\n" +
            "        ids:publicKey             <https://w3id.org/idsa/autogen/publicKey/82bae8cb-7249-457b-88e2-773e0f1b8452> ;\n" +
            "        ids:resourceCatalog       <https://vcese19.inesctec.pt:4567/connectors/-1831114251/-1076742021> , <https://vcese19.inesctec.pt:4567/connectors/-1831114251/-300705424> , <https://vcese19.inesctec.pt:4567/connectors/-1831114251/1495387289> ;\n" +
            "        ids:securityProfile       <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> ;\n" +
            "        ids:title                 \"Dataspace Connector - Confacts - subcontracted\" ;\n" +
            "        ids:version               \"8.0.9\" .\n");
        assertThat(response_INESCTEC.getBody()).isEqualTo("@prefix ids: <https://w3id.org/idsa/core/> .\n" +
            "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\n" +
            "        a                         ids:BaseConnector ;\n" +
            "        owl:sameAs                <https://test-broker.com/id/test/connector-subcontracted> ;\n" +
            "        ids:curator               <https://www.inesctec.pt/> ;\n" +
            "        ids:description           \"Description\" ;\n" +
            "        ids:hasDefaultEndpoint    <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> ;\n" +
            "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
            "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
            "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
            "        ids:publicKey             <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> ;\n" +
            "        ids:resourceCatalog       <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> ;\n" +
            "        ids:securityProfile       <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> ;\n" +
            "        ids:title                 \"Title\" .\n");
    }

    @Test
    void GETConnectors_WithElementID_With_Header_3dtPriority_NTRIPLES()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers - Priority is NTRIPLES
        // If I created them the same way as the other tests, the IDSA Broker fails because it is not counting with the whitespace in the list.
        headers.set("accept", RDF_XML +","+NTRIPLES);
        addIDSHeaders(headers, provider);

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1831114251", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-subcontracted/-821347780", HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());

        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/BaseConnector>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/BaseConnector");


        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload
        assertThat(response_IDSA.getBody()).isEqualTo("<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/curator> <https://www.inesctec.pt/> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.2\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/version> \"8.0.9\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.1\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.1.0\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.4\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/securityProfile> <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/hasDefaultEndpoint> <https://w3id.org/idsa/autogen/connectorEndpoint/39a1f291-4d95-42f0-b853-4e13c56fa925> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <http://www.w3.org/2002/07/owl#sameAs> <https://confacts.com/ids/connector/id/subcontracted> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/description> \"IDS Connector reference implementation\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/outboundModelVersion> \"4.2.7\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/resourceCatalog> <https://vcese19.inesctec.pt:4567/connectors/-1831114251/-1076742021> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.1.2\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.6\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/BaseConnector> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.7\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.5\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.0.0\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.3\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/publicKey> <https://w3id.org/idsa/autogen/publicKey/82bae8cb-7249-457b-88e2-773e0f1b8452> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/maintainer> <https://www.inesctec.pt/> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/resourceCatalog> <https://vcese19.inesctec.pt:4567/connectors/-1831114251/-300705424> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/resourceCatalog> <https://vcese19.inesctec.pt:4567/connectors/-1831114251/1495387289> .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.0\" .\n" +
            "<https://vcese19.inesctec.pt:4567/connectors/-1831114251> <https://w3id.org/idsa/core/title> \"Dataspace Connector - Confacts - subcontracted\" .\n");
        assertThat(response_INESCTEC.getBody()).isEqualTo("<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/outboundModelVersion> \"4.2.6\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/securityProfile> <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/BaseConnector> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/resourceCatalog> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/curator> <https://www.inesctec.pt/> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/hasDefaultEndpoint> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/description> \"Description\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/title> \"Title\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/publicKey> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/connector-subcontracted> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.6\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/maintainer> <https://www.inesctec.pt/> .\n");
    }

    @Test
    void getConnectors_WithElementID_With_Header_4thPriority_RDF_XML()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(RDF_XML));
        addIDSHeaders(headers, provider);

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1831114251", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-subcontracted/-821347780", HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());

        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/BaseConnector>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/BaseConnector");


        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload
        assertThat(response_IDSA.getBody()).isEqualTo("<rdf:RDF\n" +
            "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n" +
            "    xmlns:owl=\"http://www.w3.org/2002/07/owl#\"\n" +
            "    xmlns:ids=\"https://w3id.org/idsa/core/\">\n" +
            "  <ids:BaseConnector rdf:about=\"https://vcese19.inesctec.pt:4567/connectors/-1831114251\">\n" +
            "    <ids:curator rdf:resource=\"https://www.inesctec.pt/\"/>\n" +
            "    <ids:inboundModelVersion>4.2.2</ids:inboundModelVersion>\n" +
            "    <ids:version>8.0.9</ids:version>\n" +
            "    <ids:inboundModelVersion>4.2.1</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.1.0</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.2.4</ids:inboundModelVersion>\n" +
            "    <ids:securityProfile rdf:resource=\"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\"/>\n" +
            "    <ids:hasDefaultEndpoint rdf:resource=\"https://w3id.org/idsa/autogen/connectorEndpoint/39a1f291-4d95-42f0-b853-4e13c56fa925\"/>\n" +
            "    <owl:sameAs rdf:resource=\"https://confacts.com/ids/connector/id/subcontracted\"/>\n" +
            "    <ids:description>IDS Connector reference implementation</ids:description>\n" +
            "    <ids:outboundModelVersion>4.2.7</ids:outboundModelVersion>\n" +
            "    <ids:resourceCatalog rdf:resource=\"https://vcese19.inesctec.pt:4567/connectors/-1831114251/-1076742021\"/>\n" +
            "    <ids:inboundModelVersion>4.1.2</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.2.6</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.2.7</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.2.5</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.0.0</ids:inboundModelVersion>\n" +
            "    <ids:inboundModelVersion>4.2.3</ids:inboundModelVersion>\n" +
            "    <ids:publicKey rdf:resource=\"https://w3id.org/idsa/autogen/publicKey/82bae8cb-7249-457b-88e2-773e0f1b8452\"/>\n" +
            "    <ids:maintainer rdf:resource=\"https://www.inesctec.pt/\"/>\n" +
            "    <ids:resourceCatalog rdf:resource=\"https://vcese19.inesctec.pt:4567/connectors/-1831114251/-300705424\"/>\n" +
            "    <ids:resourceCatalog rdf:resource=\"https://vcese19.inesctec.pt:4567/connectors/-1831114251/1495387289\"/>\n" +
            "    <ids:inboundModelVersion>4.2.0</ids:inboundModelVersion>\n" +
            "    <ids:title>Dataspace Connector - Confacts - subcontracted</ids:title>\n" +
            "  </ids:BaseConnector>\n" +
            "</rdf:RDF>\n");
        assertThat(response_INESCTEC.getBody()).isEqualTo("<rdf:RDF\n" +
            "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n" +
            "    xmlns:owl=\"http://www.w3.org/2002/07/owl#\"\n" +
            "    xmlns:ids=\"https://w3id.org/idsa/core/\">\n" +
            "  <ids:BaseConnector rdf:about=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\">\n" +
            "    <ids:outboundModelVersion>4.2.6</ids:outboundModelVersion>\n" +
            "    <ids:securityProfile rdf:resource=\"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\"/>\n" +
            "    <ids:resourceCatalog rdf:resource=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\"/>\n" +
            "    <ids:curator rdf:resource=\"https://www.inesctec.pt/\"/>\n" +
            "    <ids:hasDefaultEndpoint rdf:resource=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\"/>\n" +
            "    <ids:description>Description</ids:description>\n" +
            "    <ids:title>Title</ids:title>\n" +
            "    <ids:publicKey rdf:resource=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\"/>\n" +
            "    <owl:sameAs rdf:resource=\"https://test-broker.com/id/test/connector-subcontracted\"/>\n" +
            "    <ids:inboundModelVersion>4.2.6</ids:inboundModelVersion>\n" +
            "    <ids:maintainer rdf:resource=\"https://www.inesctec.pt/\"/>\n" +
            "  </ids:BaseConnector>\n" +
            "</rdf:RDF>\n");

    }

    @Test
    void GETConnectors_WithElementID_With_Header_Default_Depth10() throws IOException {

        System.err.println(connectorProducer);
        System.err.println(connectorSubcontracted);
        System.err.println(testUtil.createResource());
        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(JSON_LD, RDF_XML, NTRIPLES));
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1831114251", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-subcontracted/-821347780", HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test the Response Body
        Connector connIDSA=serializer.deserialize(response_IDSA.getBody(), Connector.class);
        Connector connINESCTEC=serializer.deserialize(response_INESCTEC.getBody(), Connector.class);
        assertThat(connIDSA).isInstanceOf(Connector.class);
        assertThat(connINESCTEC).isInstanceOf(Connector.class);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/BaseConnector>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/BaseConnector");

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload

        // Assert that the Graph ID is the catalogURI
        assertThat(connIDSA.getId().toString()).isEqualTo(brokerIDSAPath+"-1831114251");
        assertThat(connINESCTEC.getId().toString()).isEqualTo(accessor.getCatalogUri() + "connector-subcontracted/-821347780");

        assertThat(connIDSA.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();
        assertThat(connINESCTEC.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();

        // Assert that the response body does not contain PublicKeys (for instance)
        assertThat(response_IDSA.getBody()).contains("ids:PublicKey");
        assertThat(response_INESCTEC.getBody()).contains("ids:PublicKey");
    }

    @Test
    void GETConnectors_GetResourceCatalog_With_Header_Default_Depth10() throws IOException {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(JSON_LD, RDF_XML, NTRIPLES));
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1831114251/-1076742021", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-subcontracted/-821347780/-878397561", HttpMethod.GET, request, String.class);


        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test the Response Body
        ResourceCatalog connIDSA=serializer.deserialize(response_IDSA.getBody(), ResourceCatalog.class);
        ResourceCatalog connINESCTEC=serializer.deserialize(response_INESCTEC.getBody(), ResourceCatalog.class);
        assertThat(connIDSA).isInstanceOf(ResourceCatalog.class);
        assertThat(connINESCTEC).isInstanceOf(ResourceCatalog.class);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/ResourceCatalog>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/ResourceCatalog");

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload
        // Assert that the Graph ID is the catalogURI
        assertThat(connIDSA.getId().toString()).isEqualTo(brokerIDSAPath+"-1831114251/-1076742021");
        assertThat(connINESCTEC.getId().toString()).isEqualTo(accessor.getCatalogUri() + "connector-subcontracted/-821347780/-878397561");

        assertThat(connIDSA.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();
        assertThat(connINESCTEC.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();
    }

    @Test
    void GETConnectors_Representation_With_Header_Default_Depth10() throws IOException {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(JSON_LD, RDF_XML, NTRIPLES));
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Prepare the headers to the Request
        HttpEntity<String> request = new HttpEntity<>(headers);

        // Request the IDSA Broker
        ResponseEntity<String>  response_IDSA=restTemplate.exchange(brokerIDSAPath+"-1771691072/116810815/945150136/-2085869294", HttpMethod.GET, request, String.class);

        // Request the INESCTEC Broker
        ResponseEntity<String>  response_INESCTEC=restTemplate.exchange(brokerLocalhostPath + "/connector-producer/-1374631466/1147089195/-435574692/1564849632", HttpMethod.GET, request, String.class);

        // Test the Response Status Code
        assertThat(response_IDSA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response_INESCTEC.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test the Response Body
        Representation connIDSA=serializer.deserialize(response_IDSA.getBody(), Representation.class);
        Representation connINESCTEC=serializer.deserialize(response_INESCTEC.getBody(), Representation.class);
        assertThat(connIDSA).isInstanceOf(Representation.class);
        assertThat(connINESCTEC).isInstanceOf(Representation.class);

        System.out.println("IDSA Response: " + response_IDSA.toString());
        System.out.println("INESCTEC Response: " + response_INESCTEC.toString());

        //Create Maps with the Headers
        Map<String, String> responseHeadersIDSA= new HashMap<>(response_IDSA.getHeaders().toSingleValueMap());
        Map<String, String> responseHeadersINESCTEC= new HashMap<>(response_INESCTEC.getHeaders().toSingleValueMap());


        // Remove all Headers that are not IDS
        responseHeadersIDSA.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));
        responseHeadersINESCTEC.keySet().removeIf(k -> ! (k.contains("ids") || k.equals("Link")));


        // Assert the value of the Headers - IDSA
        assertThat(responseHeadersIDSA.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersIDSA.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersIDSA.get("ids-issued")).isNotNull();
        assertThat(responseHeadersIDSA.get("Link")).isEqualTo("<https://w3id.org/idsa/core/Representation>");

        // Assert the value of the Headers - INESCTEC
        assertThat(responseHeadersINESCTEC.get("ids-securitytoken")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-modelversion")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-correlationmessage")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-rejectionReason")).isEqualTo(null);
        assertThat(responseHeadersINESCTEC.get("ids-issuerconnector")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-senderagent")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("ids-issued")).isNotNull();
        assertThat(responseHeadersINESCTEC.get("Link")).isEqualTo("https://w3id.org/idsa/core/Representation");

        // Assert That the Broker INESCTEC has the same IDSA Headers as the IDSA Broker
        assertThat(responseHeadersINESCTEC.keySet()).isEqualTo(responseHeadersIDSA.keySet());


        // Check the Payload

        assertThat(connIDSA.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();
        assertThat(connINESCTEC.getProperties().get("http://www.w3.org/2002/07/owl#sameAs")).isNotNull();
    }

    private static HttpHeaders addIDSHeaders(HttpHeaders headers, DapsTokenProvider provider){
        // Add mandatory headers
        try {
            headers.set("ids-securitytoken", String.valueOf(provider.provideDapsToken()));
        } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
            throw new RuntimeException(e);
        }
        headers.set("ids-modelversion", "4.0.0");
        headers.set("ids-issued", "2024-09-02T14:04:45.078Z");
        headers.set("ids-senderagent", "https://senderagent");
        headers.set("ids-issuerconnector", "https://issuerconnector");

        // Add optional IDS headers
        headers.set("ids-recipientConnector", "https://recipientConnector");
        headers.set("ids-recipientAgent", "https://recipientAgent");
        return headers;
    }
}