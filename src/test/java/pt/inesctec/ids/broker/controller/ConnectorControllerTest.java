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
import de.fraunhofer.iais.eis.ids.component.interaction.validation.ShaclValidator;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import ids.messaging.core.daps.*;
import ids.messaging.response.ErrorResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.CompareService;
import pt.inesctec.ids.broker.converter.JsonConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;
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
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.*;

// I'm using the test properties to be able to compare the output
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    locations = "classpath:application-test.properties")
class ConnectorControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorControllerTest.class);

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
    }

    @Test
    void getConnectors()  {

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.GET,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        Serializer serializer=new Serializer();

        try {
            final ConnectorCatalog catalog;
            catalog = serializer.deserialize(response.getBody().toString(), ConnectorCatalog.class);
            assertThat(catalog.getListedConnector()).isNotNull();
            System.out.println(catalog);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    void getConnectors_WithElementID()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        //headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/connector-subcontracted/-821347780", HttpMethod.GET,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(CompareUtil.compareJsonStrings(response.getBody(), "{\n" +
            "  \"@graph\" : [ {\n" +
            "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\",\n" +
            "    \"@type\" : \"ids:BaseConnector\",\n" +
            "    \"sameAs\" : \"https://test-broker.com/id/test/connector-subcontracted\",\n" +
            "    \"curator\" : \"https://www.inesctec.pt/\",\n" +
            "    \"description\" : \"Description\",\n" +
            "    \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\",\n" +
            "    \"inboundModelVersion\" : \"4.2.6\",\n" +
            "    \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
            "    \"outboundModelVersion\" : \"4.2.6\",\n" +
            "    \"publicKey\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\",\n" +
            "    \"resourceCatalog\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\",\n" +
            "    \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
            "    \"title\" : \"Title\"\n" +
            "  }, {\n" +
            "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\",\n" +
            "    \"@type\" : \"ids:PublicKey\",\n" +
            "    \"sameAs\" : \"https://test-broker.com/id/test/publicKey/gtedcsd\",\n" +
            "    \"keyType\" : \"https://w3id.org/idsa/code/RSA\",\n" +
            "    \"keyValue\" : \"S2V5VmFsdWVFeGFtcGxl\"\n" +
            "  }, {\n" +
            "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\",\n" +
            "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
            "    \"sameAs\" : \"https://test-broker.com/id/test/connectorEndpoint/gtedcsd\",\n" +
            "    \"accessURL\" : \"https://host.docker.internal:8080/test/subcontracted/api/ids/data\"\n" +
            "  }, {\n" +
            "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\",\n" +
            "    \"@type\" : \"ids:ResourceCatalog\",\n" +
            "    \"sameAs\" : \"https://test-broker.com/id/test/catalog/api/connector-subcontracted\"\n" +
            "  } ],\n" +
            "  \"@context\" : {\n" +
            "    \"sameAs\" : {\n" +
            "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"keyValue\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/keyValue\"\n" +
            "    },\n" +
            "    \"keyType\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/keyType\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"accessURL\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/accessURL\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"outboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
            "    },\n" +
            "    \"securityProfile\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"curator\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"description\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
            "    },\n" +
            "    \"title\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
            "    },\n" +
            "    \"publicKey\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"resourceCatalog\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"inboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
            "    },\n" +
            "    \"maintainer\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"hasDefaultEndpoint\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
            "  }\n" +
            "}")).isTrue();


    }

    @Test
    void getConnectors_WithElementID_InvalidElement()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        //headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/subcontracted/-821347715", HttpMethod.GET,
            request, String.class);

        System.out.println(response.toString());
        System.err.println(response.getStatusCode());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(response.getBody()).isEqualTo("The requested resource was not found");
    }

    @Test
    void getConnectorsUI_WithElementID_RequestParameter()  {
        Map<String, String> params = new HashMap<>();
        params.put("requestedElement", "connector-subcontracted/-821347780");

        ResponseEntity<String> response = this.restTemplate
            .withBasicAuth(username, password)
            .getForEntity("http://localhost:" + port + "/connectors/ui?requestedElement={requestedElement}",
                String.class,
                params
                )
            ;
        System.out.println(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(CompareUtil.compareJsonStrings(response.getBody(), "{\n" +
            "  \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\",\n" +
            "  \"@type\" : \"ids:BaseConnector\",\n" +
            "  \"sameAs\" : \"https://test-broker.com/id/test/connector-subcontracted\",\n" +
            "  \"curator\" : \"https://www.inesctec.pt/\",\n" +
            "  \"description\" : \"Description\",\n" +
            "  \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\",\n" +
            "  \"inboundModelVersion\" : \"4.2.6\",\n" +
            "  \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
            "  \"outboundModelVersion\" : \"4.2.6\",\n" +
            "  \"publicKey\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\",\n" +
            "  \"resourceCatalog\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\",\n" +
            "  \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
            "  \"title\" : \"Title\",\n" +
            "  \"@context\" : {\n" +
            "    \"outboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
            "    },\n" +
            "    \"securityProfile\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"curator\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"description\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
            "    },\n" +
            "    \"title\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
            "    },\n" +
            "    \"publicKey\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"resourceCatalog\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"sameAs\" : {\n" +
            "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"inboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
            "    },\n" +
            "    \"maintainer\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"hasDefaultEndpoint\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
            "  }\n" +
            "}")).isTrue();
    }

    @Test
    void getConnectorsUI_WithElementID_RequestParameter_ConnectorCatalog()  {
        brokerService.makeConnectorUnavailable(connectorProducer.getId());

        ResponseEntity<String> response = this.restTemplate
            .withBasicAuth(username, password)
            .getForEntity("http://localhost:" + port + "/connectors/ui",
                String.class)
            ;
        System.out.println(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(TestUtil.normalize(response.getBody())).isEqualTo(TestUtil.normalize("{\n" +
            "  \"@graph\" : [ {\n" +
            "    \"@id\" : \"http://test-hostname:8082/connectors/\",\n" +
            "    \"@type\" : \"ids:ConnectorCatalog\",\n" +
            "    \"listedConnector\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\"\n" +
            "  }, {\n" +
            "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\",\n" +
            "    \"@type\" : \"ids:BaseConnector\",\n" +
            "    \"sameAs\" : \"https://test-broker.com/id/test/connector-subcontracted\",\n" +
            "    \"curator\" : \"https://www.inesctec.pt/\",\n" +
            "    \"description\" : \"Description\",\n" +
            "    \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\",\n" +
            "    \"inboundModelVersion\" : \"4.2.6\",\n" +
            "    \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
            "    \"outboundModelVersion\" : \"4.2.6\",\n" +
            "    \"publicKey\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\",\n" +
            "    \"resourceCatalog\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\",\n" +
            "    \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
            "    \"title\" : \"Title\"\n" +
            "  } ],\n" +
            "  \"@context\" : {\n" +
            "    \"listedConnector\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/listedConnector\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"outboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
            "    },\n" +
            "    \"securityProfile\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"resourceCatalog\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"curator\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"hasDefaultEndpoint\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"description\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
            "    },\n" +
            "    \"title\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
            "    },\n" +
            "    \"publicKey\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"sameAs\" : {\n" +
            "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"inboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
            "    },\n" +
            "    \"maintainer\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
            "  }\n" +
            "}\n"));
    }


    @Test
    void getConnectors_WithElementID_With_Header_Default_With_Depth1()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(JSON_LD, RDF_XML, NTRIPLES));
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/connector-subcontracted/-821347780", HttpMethod.GET,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(CompareUtil.compareJsonStrings(response.getBody(), "{\n" +
            "  \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\",\n" +
            "  \"@type\" : \"ids:BaseConnector\",\n" +
            "  \"sameAs\" : \"https://test-broker.com/id/test/connector-subcontracted\",\n" +
            "  \"curator\" : \"https://www.inesctec.pt/\",\n" +
            "  \"description\" : \"Description\",\n" +
            "  \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\",\n" +
            "  \"inboundModelVersion\" : \"4.2.6\",\n" +
            "  \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
            "  \"outboundModelVersion\" : \"4.2.6\",\n" +
            "  \"publicKey\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\",\n" +
            "  \"resourceCatalog\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\",\n" +
            "  \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
            "  \"title\" : \"Title\",\n" +
            "  \"@context\" : {\n" +
            "    \"outboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
            "    },\n" +
            "    \"securityProfile\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"curator\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"description\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
            "    },\n" +
            "    \"title\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
            "    },\n" +
            "    \"publicKey\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"resourceCatalog\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"sameAs\" : {\n" +
            "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"inboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
            "    },\n" +
            "    \"maintainer\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"hasDefaultEndpoint\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
            "  }\n" +
            "}\n")).isTrue();
    }

    @Test
    void getConnectors_WithElementID_With_Header_Default()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(JSON_LD, RDF_XML, NTRIPLES));
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/connector-subcontracted/-821347780", HttpMethod.GET,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(CompareUtil.compareJsonStrings(response.getBody(), "{\n" +
            "  \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\",\n" +
            "  \"@type\" : \"ids:BaseConnector\",\n" +
            "  \"sameAs\" : \"https://test-broker.com/id/test/connector-subcontracted\",\n" +
            "  \"curator\" : \"https://www.inesctec.pt/\",\n" +
            "  \"description\" : \"Description\",\n" +
            "  \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\",\n" +
            "  \"inboundModelVersion\" : \"4.2.6\",\n" +
            "  \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
            "  \"outboundModelVersion\" : \"4.2.6\",\n" +
            "  \"publicKey\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\",\n" +
            "  \"resourceCatalog\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\",\n" +
            "  \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
            "  \"title\" : \"Title\",\n" +
            "  \"@context\" : {\n" +
            "    \"outboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
            "    },\n" +
            "    \"securityProfile\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"curator\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"description\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
            "    },\n" +
            "    \"title\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
            "    },\n" +
            "    \"publicKey\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"resourceCatalog\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"sameAs\" : {\n" +
            "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"inboundModelVersion\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
            "    },\n" +
            "    \"maintainer\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"hasDefaultEndpoint\" : {\n" +
            "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
            "      \"@type\" : \"@id\"\n" +
            "    },\n" +
            "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
            "  }\n" +
            "}\n")).isTrue();
    }

    @Test
    void getConnectors_WithElementID_With_Header_1stPriority()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers  - Priority is TEXT_TURTLE (However, JSON should be used)
        headers.setAccept(List.of(TEXT_TURTLE, JSON_LD, RDF_XML, NTRIPLES));
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/connector-subcontracted/-821347780", HttpMethod.GET,
            request, String.class);


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(response.getBody()).isEqualTo("@prefix ids: <https://w3id.org/idsa/core/> .\n" +
            "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466>\n" +
            "        a             ids:PublicKey ;\n" +
            "        owl:sameAs    <https://test-broker.com/id/test/publicKey/gtedcsd> ;\n" +
            "        ids:keyType   <https://w3id.org/idsa/code/RSA> ;\n" +
            "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561>\n" +
            "        a           ids:ResourceCatalog ;\n" +
            "        owl:sameAs  <https://test-broker.com/id/test/catalog/api/connector-subcontracted> .\n" +
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
            "        ids:title                 \"Title\" .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950>\n" +
            "        a              ids:ConnectorEndpoint ;\n" +
            "        owl:sameAs     <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> ;\n" +
            "        ids:accessURL  <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n");
        assertThat(CompareUtil.compareStrings(response.getBody(), "@prefix ids: <https://w3id.org/idsa/core/> .\n" +
            "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466>\n" +
            "        a             ids:PublicKey ;\n" +
            "        owl:sameAs    <https://test-broker.com/id/test/publicKey/gtedcsd> ;\n" +
            "        ids:keyType   <https://w3id.org/idsa/code/RSA> ;\n" +
            "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561>\n" +
            "        a           ids:ResourceCatalog ;\n" +
            "        owl:sameAs  <https://test-broker.com/id/test/catalog/api/connector-subcontracted> .\n" +
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
            "        ids:title                 \"Title\" .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950>\n" +
            "        a              ids:ConnectorEndpoint ;\n" +
            "        owl:sameAs     <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> ;\n" +
            "        ids:accessURL  <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .")).isTrue();
    }

    @Test
    void getConnectors_WithElementID_With_Header_3dtPriority()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers - Priority is NTRIPLES
        headers.setAccept(List.of(RDF_XML, NTRIPLES));
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/connector-subcontracted/-821347780", HttpMethod.GET,
            request, String.class);


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(CompareUtil.compareStrings(Objects.requireNonNull(response.getBody()), "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/catalog/api/connector-subcontracted> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ResourceCatalog> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <https://w3id.org/idsa/core/keyValue> \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <https://w3id.org/idsa/core/keyType> <https://w3id.org/idsa/code/RSA> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/publicKey/gtedcsd> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/PublicKey> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> <https://w3id.org/idsa/core/accessURL> <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ConnectorEndpoint> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/outboundModelVersion> \"4.2.6\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/securityProfile> <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/BaseConnector> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/curator> <https://www.inesctec.pt/> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/description> \"Description\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/title> \"Title\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/publicKey> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/resourceCatalog> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/connector-subcontracted> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.6\" .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/maintainer> <https://www.inesctec.pt/> .\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/hasDefaultEndpoint> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> .")).isTrue();
    }

    @Test
    void getConnectors_WithElementID_With_Header_4thPriority()  {

        HttpHeaders headers = new HttpHeaders();

        // Create list with all Accepted Headers
        headers.setAccept(List.of(RDF_XML));
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        headers.set("depth", "10");

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors/connector-subcontracted/-821347780", HttpMethod.GET,
            request, String.class);


        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat(CompareUtil.compareStrings(response.getBody(), "<rdf:RDF\n" +
            "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n" +
            "    xmlns:owl=\"http://www.w3.org/2002/07/owl#\"\n" +
            "    xmlns:ids=\"https://w3id.org/idsa/core/\">\n" +
            "  <ids:BaseConnector rdf:about=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780\">\n" +
            "    <ids:outboundModelVersion>4.2.6</ids:outboundModelVersion>\n" +
            "    <ids:securityProfile rdf:resource=\"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\"/>\n" +
            "    <ids:curator rdf:resource=\"https://www.inesctec.pt/\"/>\n" +
            "    <ids:description>Description</ids:description>\n" +
            "    <ids:title>Title</ids:title>\n" +
            "    <ids:publicKey>\n" +
            "      <ids:PublicKey rdf:about=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466\">\n" +
            "        <ids:keyValue>S2V5VmFsdWVFeGFtcGxl</ids:keyValue>\n" +
            "        <ids:keyType rdf:resource=\"https://w3id.org/idsa/code/RSA\"/>\n" +
            "        <owl:sameAs rdf:resource=\"https://test-broker.com/id/test/publicKey/gtedcsd\"/>\n" +
            "      </ids:PublicKey>\n" +
            "    </ids:publicKey>\n" +
            "    <ids:resourceCatalog>\n" +
            "      <ids:ResourceCatalog rdf:about=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561\">\n" +
            "        <owl:sameAs rdf:resource=\"https://test-broker.com/id/test/catalog/api/connector-subcontracted\"/>\n" +
            "      </ids:ResourceCatalog>\n" +
            "    </ids:resourceCatalog>\n" +
            "    <owl:sameAs rdf:resource=\"https://test-broker.com/id/test/connector-subcontracted\"/>\n" +
            "    <ids:inboundModelVersion>4.2.6</ids:inboundModelVersion>\n" +
            "    <ids:maintainer rdf:resource=\"https://www.inesctec.pt/\"/>\n" +
            "    <ids:hasDefaultEndpoint>\n" +
            "      <ids:ConnectorEndpoint rdf:about=\"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950\">\n" +
            "        <ids:accessURL rdf:resource=\"https://host.docker.internal:8080/test/subcontracted/api/ids/data\"/>\n" +
            "        <owl:sameAs rdf:resource=\"https://test-broker.com/id/test/connectorEndpoint/gtedcsd\"/>\n" +
            "      </ids:ConnectorEndpoint>\n" +
            "    </ids:hasDefaultEndpoint>\n" +
            "  </ids:BaseConnector>\n" +
            "</rdf:RDF>\n")).isTrue();
    }

    @Test
    void getConnectors_WithoutMandatoryHeaders()  {

        ResponseEntity<String> response = this.restTemplate
            //.withBasicAuth(username, password)
            .getForEntity("http://localhost:" + port + "/connectors",
                String.class)
            ;
        System.out.println(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Missing header: ");
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

    @Test
    void getConnectors_Unauthorize()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        //headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // Override the ids-securitytoken to set an invalid token in the correct format
        headers.set("ids-securitytoken", "eyJ0eXAiOiJhdCtqd3QiLCJraWQiOiI2MGJlZmYxYWQ2NjJlMzhmZDg5OTY2MzkyODZlM2MyNGUyYjM2NmU1MmY4N2Q4ODI1MWI4NTQwOTZlZjc4YzM5IiwiYWxnIjoiUlMyNTYifQ.eyJzY29wZSI6Im9tZWpkbjphZG1pbiIsImF1ZCI6WyJpZHNjOklEU19DT05ORUNUT1JTX0FMTCIsImh0dHBzOi8vdmNlc2UxOS5pbmVzY3RlYy5wdC9hdXRoL2FwaSJdLCJpc3MiOiJodHRwczovL3ZjZXNlMTkuaW5lc2N0ZWMucHQvYXV0aCIsInN1YiI6ImFkbWluIiwibmJmIjoxNzI1ODkyMTE3LCJpYXQiOjE3MjU4OTIxMTcsImp0aSI6IjYxNTdmZjZjLTIxNTgtNDI4OC1hYzMyLTAwZDM3OGJlMGNiMSIsImV4cCI6MTcyNTg5NTcxNywiY2xpZW50X2lkIjoicG9zdG1hbiJ9.JjQkW23GIO1GL8kJUXFBwZU4iT79lvfYBIGuoBdM20ODCplk0S_6YyU5hB_Wv13JpTVlh87xaYKmXF3R25XRWChJzJPvFqbn0egJCKRm0q2CO9xh5G8QNpoi7qm8rle6XvcY3IoWfbQ3f6KcJ6VjQDyy7LLIzN-C4_zV1grgb9LkclgzXFpwDfCFXFd3WiCQH95SN12kf3EQo2DnAUaC6KQxBQL4bOqOwphdnCi5OuYkkul-fBA--A4rY9W1_o73z0rkbY0-jBw-mSMOnkyPIHnS9U5FTCvwsUEeQSdIvTHgcnseMUIE7CcQmROmGSqICCVNhA2WOt65I246mJhgqw");


        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.GET,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isEqualTo("Error processing token.");

    }

    @Test
    void getConnectors_Unauthorize_InvalidTokenFormat()  {
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        //headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // Override the ids-securitytoken to set an invalid toknen
        headers.set("ids-securitytoken", "Invalid Token");


        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.GET,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        System.out.println(response.getBody());

        assertThat(response.getBody()).isEqualTo("Error verifying token.");

    }

    @Test
    void registerConnector()  {
        Connector connectorToInsert=testUtil.createConnector();

        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // This header is what is used to check if the Connector is already on the Database or not
        headers.set("ids-issuerconnector", connectorToInsert.getId().toString());

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(connectorToInsert.toRdf(), headers);

        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.POST,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        DocumentContext ctx = JsonPath.parse(response.getBody().toString());

        assertThat(ctx.read("$.['@type']", String.class)).isEqualTo(("ids:BaseConnector"));

        //Check if the Connector was stored on the DB
        ModelService modelService=new ModelService(accessor);
        AdminGraphService adminGraphService=new AdminGraphService(accessor);


        JenaService jenaService=new JenaService(accessor);
        assertThat(modelService.convertConnectorIDToGraphName(connectorToInsert.getId())
            .isPresent());
        assertThat(jenaService.getModel(modelService.convertConnectorIDToGraphName(connectorToInsert.getId()).get()).getModel())
            .isNotNull();



    }

    @Test
    void registerConnector_ConnectorAlreadyExists()  {

        logger.info("Start test");
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // This header is what determines if the Connector is already on the Database or not
        headers.set("ids-issuerconnector", connectorProducer.getId().toString());

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(connectorProducer.toRdf(), headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.POST,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        System.out.println(response.getBody());
        assertThat(response.getBody()).isEqualTo("The connector you are posting already exists. Use PUT instead to overwrite it.");
    }

    @Test
    void registerConnector_ConnectorWithResourceIsInvalid() throws IOException {

        logger.info("Start test");
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // This header is what determines if the Connector is already on the Database or not
        headers.set("ids-issuerconnector", "https://test.connector.de/myConnector");

        // Do the Request
        Connector connectorToInsert=testUtil.createConnector();
        Resource incompleteResource=testUtil.createResourceWithIncompleteDataModel();

        connectorToInsert.getResourceCatalog().stream().findFirst().ifPresent(catalog -> catalog.setOfferedResource(List.of(incompleteResource)));

        HttpEntity<String> request = new HttpEntity<>(connectorToInsert.toRdf(), headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.POST,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        System.out.println(response.getBody());
        assertThat(response.getBody()).isEqualTo("It needs to have a Representation.");

    }

    @Test
    void registerConnector_ConnectorWithResource()  {

        logger.info("Start test");
        // Create list with all Accepted Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        addIDSHeaders(headers, provider);
        // This header is what determines if the Connector is already on the Database or not
        headers.set("ids-issuerconnector", "https://demo.inesctec.pt/postmanREST");

        Connector connectorToInsert=testUtil.createConnector();
        connectorToInsert.getResourceCatalog().stream().findFirst().ifPresent(catalog -> catalog.setOfferedResource(List.of(testUtil.createResource())));

        // Do the Request
        HttpEntity<String> request = new HttpEntity<>(this.getConnectorWithResource(), headers);
        var response=restTemplate.exchange("http://localhost:" + port + "/connectors", HttpMethod.POST,
            request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        JenaService jenaService=new JenaService(accessor);
        ModelService modelService=new ModelService(accessor);
        ModelDto modelDto=jenaService.getModel(modelService.convertConnectorIDToGraphName(URI.create("https://demo.inesctec.pt/postmanREST")).get());

        modelDto.getModel().write(System.out, "Turtle");

        assertThat(modelDto.getModel().listStatements(null, ResourceFactory.createProperty(SAME_AS), ResourceFactory.createResource("https://example.org/dummyInitialResource")))
            .isNotNull();
        assertThat(modelDto.getModel().listStatements(null, ResourceFactory.createProperty(SAME_AS), ResourceFactory.createResource("https://example.org/dummyInitialResource")).toList().size())
            .isEqualTo(1);
    }

    private String getConnectorWithResource(){
        return "{\n" +
            "  \"@context\" : {\n" +
            "    \"ids\" : \"https://w3id.org/idsa/core/\",\n" +
            "    \"idsc\" : \"https://w3id.org/idsa/code/\"\n" +
            "  },\n" +
            "  \"@type\" : \"ids:BaseConnector\",\n" +
            "  \"@id\" : \"https://demo.inesctec.pt/postmanREST\",\n" +
            "  \"ids:maintainer\" : {\n" +
            "    \"@id\" : \"https://example.org/\"\n" +
            "  },\n" +
            "  \"ids:securityProfile\" : {\n" +
            "    \"@id\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\"\n" +
            "  },\n" +
            "  \"ids:curator\" : {\n" +
            "    \"@id\" : \"https://example.org/\"\n" +
            "  },\n" +
            "  \"ids:resourceCatalog\" : [ {\n" +
            "    \"@type\" : \"ids:ResourceCatalog\",\n" +
            "    \"@id\" : \"https://w3id.org/idsa/autogen/resourceCatalog/5696416c-2e5e-4db9-9e3e-6da210feb5e1\",\n" +
            "    \"ids:offeredResource\" : [ {\n" +
            "      \"@type\" : \"ids:Resource\",\n" +
            "      \"@id\" : \"https://example.org/dummyInitialResource\",\n" +
            "      \"ids:language\" : [ {\n" +
            "        \"@id\" : \"https://w3id.org/idsa/code/DE\"\n" +
            "      }, {\n" +
            "        \"@id\" : \"https://w3id.org/idsa/code/ES\"\n" +
            "      } ],\n" +
            "      \"ids:version\" : \"v0.0.1\",\n" +
            "      \"ids:description\" : [ {\n" +
            "        \"@value\" : \"Lorem ipsum dolor sit amet, consectetur adipisicing elit. Quos blanditiis tenetur unde suscipit, quam beatae rerum inventore consectetur, neque doloribus, cupiditate numquam dignissimos laborum fugiat deleniti? Eum quasi quidem quibusdam.\",\n" +
            "        \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "      } ],\n" +
            "      \"ids:resourceEndpoint\" : [ {\n" +
            "        \"@type\" : \"ids:ConnectorEndpoint\",\n" +
            "        \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/aae23e6b-3f58-4bed-9536-7c5e5d4112bf\",\n" +
            "        \"ids:path\" : \"a Static endpoint path\",\n" +
            "        \"ids:endpointArtifact\" : {\n" +
            "          \"@type\" : \"ids:Artifact\",\n" +
            "          \"@id\" : \"https://w3id.org/idsa/autogen/artifact/14311867-c14d-4e8d-9402-d11ce29d51af\",\n" +
            "          \"ids:fileName\" : \"staticfile.txt\",\n" +
            "          \"ids:byteSize\" : 150,\n" +
            "          \"ids:creationDate\" : {\n" +
            "            \"@value\" : \"2000-10-15T00:00:00.000Z\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
            "          },\n" +
            "          \"ids:checkSum\" : \"artifact checksum\"\n" +
            "        },\n" +
            "        \"ids:accessURL\" : {\n" +
            "          \"@id\" : \"http://example.org/accessURL\"\n" +
            "        },\n" +
            "        \"ids:inboundPath\" : \"a static inbound path\",\n" +
            "        \"ids:outboundPath\" : \"a static outbound path\"\n" +
            "      } ],\n" +
            "      \"ids:contentType\" : {\n" +
            "        \"@id\" : \"https://w3id.org/idsa/code/SCHEMA_DEFINITION\"\n" +
            "      },\n" +
            "      \"ids:contractOffer\" : [ {\n" +
            "        \"@type\" : \"ids:ContractOffer\",\n" +
            "        \"@id\" : \"https://w3id.org/idsa/autogen/contractOffer/54789dd3-2ab3-4db6-be67-2ac80a021aac\",\n" +
            "        \"ids:permission\" : [ {\n" +
            "          \"@type\" : \"ids:Permission\",\n" +
            "          \"@id\" : \"https://w3id.org/idsa/autogen/permission/48710e40-af32-45f4-ad1f-f821958d135e\",\n" +
            "          \"ids:description\" : [ {\n" +
            "            \"@value\" : \"permission description\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "          } ],\n" +
            "          \"ids:title\" : [ {\n" +
            "            \"@value\" : \"permission title\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "          } ],\n" +
            "          \"ids:assigner\" : [ {\n" +
            "            \"@id\" : \"https://example.org/someDuplicateAssignee\"\n" +
            "          } ],\n" +
            "          \"ids:assignee\" : [ {\n" +
            "            \"@id\" : \"https://example.org/someDuplicateAssignee\"\n" +
            "          } ],\n" +
            "          \"ids:action\" : [ {\n" +
            "            \"@id\" : \"https://w3id.org/idsa/code/READ\"\n" +
            "          } ],\n" +
            "          \"ids:constraint\" : [ {\n" +
            "            \"@type\" : \"ids:Constraint\",\n" +
            "            \"@id\" : \"https://w3id.org/idsa/autogen/constraint/27727809-1c53-4eaa-9571-04e4736af6c2\",\n" +
            "            \"ids:rightOperand\" : {\n" +
            "              \"@value\" : \"200\",\n" +
            "              \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "            },\n" +
            "            \"ids:unit\" : {\n" +
            "              \"@id\" : \"http://example.org/unit\"\n" +
            "            },\n" +
            "            \"ids:leftOperand\" : {\n" +
            "              \"@id\" : \"https://w3id.org/idsa/code/PAY_AMOUNT\"\n" +
            "            },\n" +
            "            \"ids:operator\" : {\n" +
            "              \"@id\" : \"https://w3id.org/idsa/code/EQUALS\"\n" +
            "            }\n" +
            "          } ],\n" +
            "          \"ids:preDuty\" : [ {\n" +
            "            \"@type\" : \"ids:Duty\",\n" +
            "            \"@id\" : \"https://w3id.org/idsa/autogen/duty/c3870018-384b-422f-8c12-22e9a6486194\",\n" +
            "            \"ids:description\" : [ {\n" +
            "              \"@value\" : \"Description of the pre-duty\",\n" +
            "              \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "            } ],\n" +
            "            \"ids:title\" : [ {\n" +
            "              \"@value\" : \"Title Pre-Duty\",\n" +
            "              \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "            } ],\n" +
            "            \"ids:assigner\" : [ {\n" +
            "              \"@id\" : \"http://example.org/preDutyAssigner\"\n" +
            "            } ],\n" +
            "            \"ids:assignee\" : [ {\n" +
            "              \"@id\" : \"http://example.org/preDutyAssignee\"\n" +
            "            } ],\n" +
            "            \"ids:action\" : [ {\n" +
            "              \"@id\" : \"https://w3id.org/idsa/code/ANONYMIZE\"\n" +
            "            } ],\n" +
            "            \"ids:constraint\" : [ {\n" +
            "              \"@type\" : \"ids:Constraint\",\n" +
            "              \"@id\" : \"https://w3id.org/idsa/autogen/constraint/c8d71b95-8820-450b-ad9b-7fdce0d7aed6\",\n" +
            "              \"ids:rightOperand\" : {\n" +
            "                \"@value\" : \"5\",\n" +
            "                \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "              },\n" +
            "              \"ids:unit\" : {\n" +
            "                \"@id\" : \"http://example.org/unit\"\n" +
            "              },\n" +
            "              \"ids:leftOperand\" : {\n" +
            "                \"@id\" : \"https://w3id.org/idsa/code/QUANTITY\"\n" +
            "              },\n" +
            "              \"ids:operator\" : {\n" +
            "                \"@id\" : \"https://w3id.org/idsa/code/EQUALS\"\n" +
            "              }\n" +
            "            } ]\n" +
            "          } ]\n" +
            "        } ],\n" +
            "        \"ids:provider\" : {\n" +
            "          \"@id\" : \"http://provider.org\"\n" +
            "        },\n" +
            "        \"ids:contractDocument\" : {\n" +
            "          \"@type\" : \"ids:TextResource\",\n" +
            "          \"@id\" : \"https://w3id.org/idsa/autogen/textResource/366517fa-483c-4779-a53e-57c1453ac258\",\n" +
            "          \"ids:description\" : [ {\n" +
            "            \"@value\" : \"Description of the textual contract document\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "          } ],\n" +
            "          \"ids:title\" : [ {\n" +
            "            \"@value\" : \"Title of the textual contract document\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "          } ]\n" +
            "        },\n" +
            "        \"ids:contractDate\" : {\n" +
            "          \"@value\" : \"2000-07-30T00:00:00.000Z\",\n" +
            "          \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
            "        },\n" +
            "        \"ids:prohibition\" : [ {\n" +
            "          \"@type\" : \"ids:Prohibition\",\n" +
            "          \"@id\" : \"https://w3id.org/idsa/autogen/prohibition/ff9f0d17-7176-4553-a43e-74bc545d6b18\",\n" +
            "          \"ids:description\" : [ {\n" +
            "            \"@value\" : \"prohibition description\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "          } ],\n" +
            "          \"ids:title\" : [ {\n" +
            "            \"@value\" : \"prohibition title\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "          } ],\n" +
            "          \"ids:assigner\" : [ {\n" +
            "            \"@id\" : \"http://example.org/assigner1\"\n" +
            "          }, {\n" +
            "            \"@id\" : \"http://example.org/assigner2\"\n" +
            "          } ],\n" +
            "          \"ids:assignee\" : [ {\n" +
            "            \"@id\" : \"http://example.org/assignee1\"\n" +
            "          }, {\n" +
            "            \"@id\" : \"http://example.org/assignee2\"\n" +
            "          } ],\n" +
            "          \"ids:action\" : [ {\n" +
            "            \"@id\" : \"https://w3id.org/idsa/code/DISTRIBUTE\"\n" +
            "          } ],\n" +
            "          \"ids:constraint\" : [ {\n" +
            "            \"@type\" : \"ids:Constraint\",\n" +
            "            \"@id\" : \"https://w3id.org/idsa/autogen/constraint/c0e9cc0a-5928-4e73-8e2e-7051e347ea49\",\n" +
            "            \"ids:rightOperand\" : {\n" +
            "              \"@value\" : \"24 months\",\n" +
            "              \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "            },\n" +
            "            \"ids:unit\" : {\n" +
            "              \"@id\" : \"http://example.org/unit\"\n" +
            "            },\n" +
            "            \"ids:leftOperand\" : {\n" +
            "              \"@id\" : \"https://w3id.org/idsa/code/POLICY_EVALUATION_TIME\"\n" +
            "            },\n" +
            "            \"ids:operator\" : {\n" +
            "              \"@id\" : \"https://w3id.org/idsa/code/DEFINES_AS\"\n" +
            "            }\n" +
            "          } ]\n" +
            "        } ],\n" +
            "        \"ids:contractEnd\" : {\n" +
            "          \"@value\" : \"2001-06-19T00:00:00.000Z\",\n" +
            "          \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
            "        },\n" +
            "        \"ids:consumer\" : {\n" +
            "          \"@id\" : \"http://consumer.org\"\n" +
            "        },\n" +
            "        \"ids:contractStart\" : {\n" +
            "          \"@value\" : \"2000-10-20T00:00:00.000Z\",\n" +
            "          \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
            "        }\n" +
            "      } ],\n" +
            "      \"ids:customLicense\" : {\n" +
            "        \"@id\" : \"http://customlicense.org\"\n" +
            "      },\n" +
            "      \"ids:representation\" : [ {\n" +
            "        \"@type\" : \"ids:Representation\",\n" +
            "        \"@id\" : \"https://w3id.org/idsa/autogen/textRepresentation/577ff12b-ee07-48a5-a6d1-0bfda1a27819\",\n" +
            "        \"ids:instance\" : [ {\n" +
            "          \"@type\" : \"ids:Artifact\",\n" +
            "          \"@id\" : \"https://w3id.org/idsa/autogen/artifact/0195f0d3-0ee9-4337-a6fc-4d1e6388c06b\",\n" +
            "          \"ids:fileName\" : \"data.pdf\",\n" +
            "          \"ids:byteSize\" : 2678,\n" +
            "          \"ids:creationDate\" : {\n" +
            "            \"@value\" : \"2015-10-15T00:00:00.000Z\",\n" +
            "            \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
            "          }\n" +
            "        } ],\n" +
            "        \"ids:representationStandard\" : {\n" +
            "          \"@id\" : \"http://textRepresentation.org\"\n" +
            "        },\n" +
            "        \"ids:mediaType\" : {\n" +
            "          \"@type\" : \"ids:IANAMediaType\",\n" +
            "          \"@id\" : \"https://w3id.org/idsa/autogen/iANAMediaType/f61fd156-3f85-4623-b820-3c96498e7c6b\",\n" +
            "          \"ids:filenameExtension\" : \"pdf\"\n" +
            "        }\n" +
            "      } ],\n" +
            "      \"ids:standardLicense\" : {\n" +
            "        \"@id\" : \"http://example.org/license\"\n" +
            "      },\n" +
            "      \"ids:keyword\" : [ {\n" +
            "        \"@value\" : \"demo\",\n" +
            "        \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "      }, {\n" +
            "        \"@value\" : \"test\",\n" +
            "        \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "      } ],\n" +
            "      \"ids:contentStandard\" : {\n" +
            "        \"@id\" : \"http://contentstandard.org\"\n" +
            "      },\n" +
            "      \"ids:title\" : [ {\n" +
            "        \"@value\" : \"Title of the resource offered by the Connector\",\n" +
            "        \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "      } ]\n" +
            "    } ]\n" +
            "  } ],\n" +
            "  \"ids:description\" : [ {\n" +
            "    \"@value\" : \"This is a dummy description from the Interaction Library\",\n" +
            "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "  } ],\n" +
            "  \"ids:outboundModelVersion\" : \"4.0.0\",\n" +
            "  \"ids:hasDefaultEndpoint\" : {\n" +
            "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
            "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/085664aa-5365-45e9-a2cf-e3b660b2d410\",\n" +
            "    \"ids:accessURL\" : {\n" +
            "      \"@id\" : \"https://example.org/endpoint\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"ids:inboundModelVersion\" : [ \"4.0.0\" ],\n" +
            "  \"ids:title\" : [ {\n" +
            "    \"@value\" : \"This is a dummy title from the Interaction Library\",\n" +
            "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#string\"\n" +
            "  } ]\n" +
            "}";
    }
}