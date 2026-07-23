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
import de.fraunhofer.iais.eis.ids.component.core.RejectMessageException;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import ids.messaging.common.SerializeException;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.IsSelfDescriptionException;
import pt.inesctec.ids.broker.util.CompareUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.DEPTH;
import static pt.inesctec.ids.broker.config.ResponseType.*;

@SpringBootTest
@TestPropertySource(
    locations = "classpath:application-test.properties")
class DescriptionServiceTest {

  @Autowired
  FusekiConnectionAccessor accessor;

  @Autowired
  @Qualifier("catalogURI")
  private String catalogURI;

  DescriptionService descriptionService=new DescriptionService(accessor);
  StringWriter stringWriter = new StringWriter();
  Serializer serializer=new Serializer();
  TestUtil testUtil=new TestUtil();

  @BeforeEach
  void setUp() {
    BrokerService brokerService=new BrokerService(accessor);
    Connector connector_producer=testUtil.createConnector();
    Connector connector_subcontracted=testUtil.createConnectorSubcontracted();
    Connector connector_producer2=testUtil.createConnectorWithMultipleCatalogs();
    Resource resource_producer=testUtil.createResourceWithMultipleArtefacts();
    Resource resource_subcontracted=testUtil.createResource();

    testUtil.removeAllGraphsFromDB(accessor);


    brokerService.addConnectorToFuseki(connector_producer);
    brokerService.addConnectorToFuseki(connector_subcontracted);
    brokerService.addConnectorToFuseki(connector_producer2);

    brokerService.addResourceToFuseki(resource_producer, connector_producer2.getId());
    brokerService.addResourceToFuseki(resource_subcontracted, connector_subcontracted.getId());
  }

  @Test
  void description() throws RejectMessageException, IsSelfDescriptionException {
    // Run test
    Model resultModel= descriptionService.description(catalogURI, URI.create(catalogURI), accessor, 1);

    // Present test result in JSON format to compare
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD10_COMPACT_PRETTY);

    final ConnectorCatalog requestPayload;
    try {
      requestPayload = serializer.deserialize(stringWriter.toString(), ConnectorCatalog.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertTrue(ConnectorCatalogImpl.class.isAssignableFrom(requestPayload.getClass()));

    System.err.println("Size: "+new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size());
    assertThat(new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size())
        .isEqualTo(180);
  }

  @Test
  void description_with_default_depth() throws RejectMessageException, IsSelfDescriptionException {
    // Run test
    Model resultModel= descriptionService.description(catalogURI, URI.create(catalogURI), accessor);

    // Present test result in JSON format to compare
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD10_COMPACT_PRETTY);

    final ConnectorCatalog requestPayload;
    try {
      requestPayload = serializer.deserialize(stringWriter.toString(), ConnectorCatalog.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertTrue(ConnectorCatalogImpl.class.isAssignableFrom(requestPayload.getClass()));

    System.err.println("Size: "+new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size());
    assertThat(new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size())
        .isEqualTo(84);
  }

  @Test
  void description_with_depth10() throws RejectMessageException, IsSelfDescriptionException {
    // Run test
    Model resultModel= descriptionService.description(catalogURI, URI.create(catalogURI), accessor, 10);

    // Present test result in JSON format to compare
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD10_COMPACT_PRETTY);

    final ConnectorCatalog requestPayload;
    try {
      requestPayload = serializer.deserialize(stringWriter.toString(), ConnectorCatalog.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertTrue(ConnectorCatalogImpl.class.isAssignableFrom(requestPayload.getClass()));

    System.err.println("Size: "+new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size());
    assertThat(new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size())
        .isEqualTo(443);
  }

  /**
   * RequestedElement = "connector-producer/-1374631466";
   * Depth=0
   * */
  @Test
  void description_with_default_depth_custom_element() throws RejectMessageException, IsSelfDescriptionException {
    // Run test
    Model resultModel= descriptionService.description(catalogURI, URI.create(catalogURI+"connector-producer/-1374631466"), accessor);

    // Present test result in JSON format to compare
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD10_COMPACT_PRETTY);
    System.out.println(stringWriter.toString());

    // Check the response
    System.err.println("Size: "+new ArrayList<>(Arrays.asList(stringWriter.toString().toString().split("\n"))).size());
    assertThat(new ArrayList<>(Arrays.asList(stringWriter.toString().toString().split("\n"))).size())
        .isEqualTo(59);
    assertThat(CompareUtil.compareJsonStrings(stringWriter.toString(), "{\n" +
        "  \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466\",\n" +
        "  \"@type\" : \"ids:BaseConnector\",\n" +
        "  \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/connector-producer\",\n" +
        "  \"curator\" : \"https://www.inesctec.pt/\",\n" +
        "  \"description\" : \"Description\",\n" +
        "  \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774\",\n" +
        "  \"inboundModelVersion\" : \"4.2.6\",\n" +
        "  \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
        "  \"outboundModelVersion\" : \"4.2.6\",\n" +
        "  \"publicKey\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034\",\n" +
        "  \"resourceCatalog\" : [ \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195\", \"http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134\" ],\n" +
        "  \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
        "  \"title\" : \"Title\",\n" +
        "  \"@context\" : {\n" +
        "    \"maintainer\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"hasDefaultEndpoint\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"resourceCatalog\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"outboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
        "    },\n" +
        "    \"inboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
        "    },\n" +
        "    \"publicKey\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"description\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
        "    },\n" +
        "    \"sameAs\" : {\n" +
        "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"securityProfile\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"title\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
        "    },\n" +
        "    \"curator\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
        "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
        "  }\n" +
        "}")).isTrue();

  }

  /**
   * RequestedElement = "connector-producer/-1374631466";
   * Depth=10
   * */
  @Test
  void description_with_depth10_custom_element() throws RejectMessageException, IsSelfDescriptionException {
    // Run test
    Model resultModel= descriptionService.description(catalogURI, URI.create(catalogURI+"connector-producer/-1374631466"), accessor, 10);

    // Present test result in JSON format to compare
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD10_COMPACT_PRETTY);
    System.out.println(stringWriter.toString());

    // Check the response
    System.err.println("Size: "+new ArrayList<>(Arrays.asList(stringWriter.toString().toString().split("\n"))).size());
    assertThat(new ArrayList<>(Arrays.asList(stringWriter.toString().toString().split("\n"))).size())
        .isEqualTo(254);
    assertThat(CompareUtil.compareJsonStrings(stringWriter.toString(), "{\n" +
        "  \"@graph\" : [ {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466\",\n" +
        "    \"@type\" : \"ids:BaseConnector\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/connector-producer\",\n" +
        "    \"curator\" : \"https://www.inesctec.pt/\",\n" +
        "    \"description\" : \"Description\",\n" +
        "    \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774\",\n" +
        "    \"inboundModelVersion\" : \"4.2.6\",\n" +
        "    \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
        "    \"outboundModelVersion\" : \"4.2.6\",\n" +
        "    \"publicKey\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034\",\n" +
        "    \"resourceCatalog\" : [ \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195\", \"http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134\" ],\n" +
        "    \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
        "    \"title\" : \"Title\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195\",\n" +
        "    \"@type\" : \"ids:ResourceCatalog\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer\",\n" +
        "    \"offeredResource\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692\",\n" +
        "    \"@type\" : \"ids:Resource\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5\",\n" +
        "    \"contractOffer\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807\",\n" +
        "    \"created\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"description\" : \"Information about product P20230303\",\n" +
        "    \"keyword\" : \"term1\",\n" +
        "    \"language\" : \"https://w3id.org/idsa/code/EN\",\n" +
        "    \"modified\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"representation\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632\",\n" +
        "    \"resourceEndpoint\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2\",\n" +
        "    \"title\" : \"PDP-P20230303\",\n" +
        "    \"version\" : \"1\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807\",\n" +
        "    \"@type\" : \"ids:ContractOffer\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/contracts/30481515-3fe0-459e-a08b-57cf17204d0f\",\n" +
        "    \"contractDate\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"contractEnd\" : \"2025-03-23T12:23:22Z\",\n" +
        "    \"contractStart\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"permission\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913\",\n" +
        "    \"@type\" : \"ids:Permission\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7\",\n" +
        "    \"action\" : \"https://w3id.org/idsa/code/USE\",\n" +
        "    \"description\" : \"PROVIDE_ACCESS\",\n" +
        "    \"title\" : \"PROVIDE_ACCESS\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632\",\n" +
        "    \"@type\" : \"ids:Representation\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/representations/ab2a6919-c7da-4df2-9a2a-9a508beea249\",\n" +
        "    \"created\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"instance\" : [ \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031\", \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726\", \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208\", \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366\" ],\n" +
        "    \"language\" : \"https://w3id.org/idsa/code/EN\",\n" +
        "    \"mediaType\" : \"https://localhost:8080/iANAMediaType/cnasoe0\",\n" +
        "    \"modified\" : \"2022-03-23T12:23:22Z\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031\",\n" +
        "    \"@type\" : \"ids:Artifact\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/artifacts/dweytgw76da-e1af-46a1-9f42-cnasoe0\",\n" +
        "    \"ids:byteSize\" : 4,\n" +
        "    \"checkSum\" : \"0\",\n" +
        "    \"creationDate\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"fileName\" : \"file4\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208\",\n" +
        "    \"@type\" : \"ids:Artifact\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/artifacts/cduerfha9s-e1af-46a1-9f42-ndkfhcne8endaj\",\n" +
        "    \"ids:byteSize\" : 2,\n" +
        "    \"checkSum\" : \"0\",\n" +
        "    \"creationDate\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"fileName\" : \"file2\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366\",\n" +
        "    \"@type\" : \"ids:Artifact\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/artifacts/xdweudnja-e1af-46a1-9f42-384hnd\",\n" +
        "    \"ids:byteSize\" : 1,\n" +
        "    \"checkSum\" : \"0\",\n" +
        "    \"creationDate\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"fileName\" : \"file3\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726\",\n" +
        "    \"@type\" : \"ids:Artifact\",\n" +
        "    \"sameAs\" : \"https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a\",\n" +
        "    \"ids:byteSize\" : 0,\n" +
        "    \"checkSum\" : \"0\",\n" +
        "    \"creationDate\" : \"2022-03-23T12:23:22Z\",\n" +
        "    \"fileName\" : \"test-file\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134\",\n" +
        "    \"@type\" : \"ids:ResourceCatalog\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/catalog/2/api/connector-producer\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774\",\n" +
        "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/connectorEndpoint/dhiasdcna\",\n" +
        "    \"accessURL\" : \"https://host.docker.internal:8080/test/multipleCatalogs/connector/api/ids/data\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034\",\n" +
        "    \"@type\" : \"ids:PublicKey\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/publicKey/dferstgse\",\n" +
        "    \"keyType\" : \"https://w3id.org/idsa/code/RSA\",\n" +
        "    \"keyValue\" : \"S2V5VmFsdWVFeGFtcGxl\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"https://localhost:8080/iANAMediaType/cnasoe0\",\n" +
        "    \"@type\" : \"ids:IANAMediaType\",\n" +
        "    \"filenameExtension\" : \"application/octet-stream\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2\",\n" +
        "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
        "    \"accessURL\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692\",\n" +
        "    \"endpointDocumentation\" : \"https://url.for.documentation\"\n" +
        "  } ],\n" +
        "  \"@context\" : {\n" +
        "    \"sameAs\" : {\n" +
        "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"offeredResource\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/offeredResource\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"filenameExtension\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/filenameExtension\"\n" +
        "    },\n" +
        "    \"endpointDocumentation\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/endpointDocumentation\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"accessURL\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/accessURL\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"fileName\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/fileName\"\n" +
        "    },\n" +
        "    \"creationDate\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/creationDate\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
        "    },\n" +
        "    \"checkSum\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/checkSum\"\n" +
        "    },\n" +
        "    \"byteSize\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/byteSize\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#integer\"\n" +
        "    },\n" +
        "    \"language\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/language\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"mediaType\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/mediaType\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"modified\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/modified\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
        "    },\n" +
        "    \"instance\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/instance\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"created\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/created\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
        "    },\n" +
        "    \"keyValue\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyValue\"\n" +
        "    },\n" +
        "    \"keyType\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyType\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"permission\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/permission\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"contractStart\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/contractStart\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
        "    },\n" +
        "    \"contractEnd\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/contractEnd\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
        "    },\n" +
        "    \"contractDate\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/contractDate\",\n" +
        "      \"@type\" : \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\"\n" +
        "    },\n" +
        "    \"resourceCatalog\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"maintainer\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"outboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
        "    },\n" +
        "    \"inboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
        "    },\n" +
        "    \"description\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
        "    },\n" +
        "    \"publicKey\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"securityProfile\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"title\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
        "    },\n" +
        "    \"curator\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"hasDefaultEndpoint\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"contractOffer\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/contractOffer\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"resourceEndpoint\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/resourceEndpoint\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"version\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/version\"\n" +
        "    },\n" +
        "    \"keyword\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyword\"\n" +
        "    },\n" +
        "    \"representation\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/representation\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"action\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/action\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
        "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
        "  }\n" +
        "}")).isTrue();

  }

  /**
   * RequestedElement = "connector-producer/-1374631466";
   * Depth=1
   * */
  @Test
  void description_with_depth_1_custom_element() throws RejectMessageException, IsSelfDescriptionException {
    // Run test
    Model resultModel= descriptionService.description(catalogURI, URI.create(catalogURI+"connector-producer/-1374631466"), accessor, 1);

    // Present test result in JSON format to compare
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD10_COMPACT_PRETTY);
    System.out.println(stringWriter.toString());

    // Check the response
    System.err.println("Size: "+new ArrayList<>(Arrays.asList(stringWriter.toString().toString().split("\n"))).size());
    assertThat(new ArrayList<>(Arrays.asList(stringWriter.toString().toString().split("\n"))).size())
        .isEqualTo(96);
    assertThat(CompareUtil.compareJsonStrings(stringWriter.toString(), "{\n" +
        "  \"@graph\" : [ {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466\",\n" +
        "    \"@type\" : \"ids:BaseConnector\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/connector-producer\",\n" +
        "    \"curator\" : \"https://www.inesctec.pt/\",\n" +
        "    \"description\" : \"Description\",\n" +
        "    \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774\",\n" +
        "    \"inboundModelVersion\" : \"4.2.6\",\n" +
        "    \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
        "    \"outboundModelVersion\" : \"4.2.6\",\n" +
        "    \"publicKey\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034\",\n" +
        "    \"resourceCatalog\" : [ \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195\", \"http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134\" ],\n" +
        "    \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
        "    \"title\" : \"Title\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195\",\n" +
        "    \"@type\" : \"ids:ResourceCatalog\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer\",\n" +
        "    \"offeredResource\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134\",\n" +
        "    \"@type\" : \"ids:ResourceCatalog\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/catalog/2/api/connector-producer\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774\",\n" +
        "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/connectorEndpoint/dhiasdcna\",\n" +
        "    \"accessURL\" : \"https://host.docker.internal:8080/test/multipleCatalogs/connector/api/ids/data\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034\",\n" +
        "    \"@type\" : \"ids:PublicKey\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/multipleCatalogs/publicKey/dferstgse\",\n" +
        "    \"keyType\" : \"https://w3id.org/idsa/code/RSA\",\n" +
        "    \"keyValue\" : \"S2V5VmFsdWVFeGFtcGxl\"\n" +
        "  } ],\n" +
        "  \"@context\" : {\n" +
        "    \"sameAs\" : {\n" +
        "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"offeredResource\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/offeredResource\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"keyValue\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyValue\"\n" +
        "    },\n" +
        "    \"keyType\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyType\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"resourceCatalog\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"maintainer\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"outboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
        "    },\n" +
        "    \"inboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
        "    },\n" +
        "    \"description\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
        "    },\n" +
        "    \"publicKey\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"securityProfile\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"title\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
        "    },\n" +
        "    \"curator\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"hasDefaultEndpoint\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"accessURL\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/accessURL\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
        "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
        "  }\n" +
        "}")).isTrue();

  }


  /**
   * RequestedElement = "connector/-123456";
   * Depth=10
   * Also applies to the inactive Connectors
   * */
  @Test
  void description_with_invalid_custom_element() {
    // Run test
    Exception exception =
        assertThrows(RejectMessageException.class, () -> descriptionService.description(catalogURI, URI.create(catalogURI+"connector/-123456"), accessor, 10));

    assertThat(exception.getCause().getMessage())
        .isEqualTo("The requested resource was not found");
    assertThat(((RejectMessageException)exception).getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);

  }


  @Test
  void returnOutputFormat_DescriptionMessageAsParameter_WithArrayList()  {
    // Build Message
    DescriptionRequestMessage message=new DescriptionRequestMessageBuilder()
        ._securityToken_(new DynamicAttributeTokenBuilder()
            ._tokenValue_("Fake Token")
            ._tokenFormat_(TokenFormat.JWT)
            .build())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(URI.create("https://issuerConnector"))
        ._modelVersion_("4.0.2")
        ._senderAgent_(URI.create("https://senderAgent"))
        ._recipientConnector_(URI.create("https://localwslhost:8082/api/ids/data"))
        ._recipientAgent_(URI.create("https://localwslhost:8082/api/ids/data"))
        .build();

    List<TypedLiteral> acceptList=new ArrayList<>();
    acceptList.add(new TypedLiteral(TURTLE));
    acceptList.add(new TypedLiteral(NTRIPLES));
    message.setProperty("https://w3id.org/idsa/core/accept", acceptList);

    // Run test
    RDFFormat resultFormat= descriptionService.returnOutputFormat((DescriptionRequestMessageImpl) message);

    // Check the response
    assertThat(resultFormat).isEqualTo(RDFFormat.TURTLE);
  }

  @Test
  void returnOutputFormat_DescriptionMessageAsParameter_WithTypedLiteral()  {
    // Build Message
    DescriptionRequestMessage message=new DescriptionRequestMessageBuilder()
        ._securityToken_(new DynamicAttributeTokenBuilder()
            ._tokenValue_("Fake Token")
            ._tokenFormat_(TokenFormat.JWT)
            .build())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(URI.create("https://issuerConnector"))
        ._modelVersion_("4.0.2")
        ._senderAgent_(URI.create("https://senderAgent"))
        ._recipientConnector_(URI.create("https://localwslhost:8082/api/ids/data"))
        ._recipientAgent_(URI.create("https://localwslhost:8082/api/ids/data"))
        .build();

    message.setProperty("https://w3id.org/idsa/core/accept", new TypedLiteral(TURTLE + "," + NTRIPLES));

    // Run test
    RDFFormat resultFormat= descriptionService.returnOutputFormat((DescriptionRequestMessageImpl) message);

    // Check the response
    assertThat(resultFormat).isEqualTo(RDFFormat.TURTLE);
  }

  @Test
  void returnOutputFormat_DescriptionMessageAsParameter_WithStrings()  {
    // Build Message
    DescriptionRequestMessage message=new DescriptionRequestMessageBuilder()
        ._securityToken_(new DynamicAttributeTokenBuilder()
            ._tokenValue_("Fake Token")
            ._tokenFormat_(TokenFormat.JWT)
            .build())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(URI.create("https://issuerConnector"))
        ._modelVersion_("4.0.2")
        ._senderAgent_(URI.create("https://senderAgent"))
        ._recipientConnector_(URI.create("https://localwslhost:8082/api/ids/data"))
        ._recipientAgent_(URI.create("https://localwslhost:8082/api/ids/data"))
        .build();

    message.setProperty("https://w3id.org/idsa/core/accept", NTRIPLES);

    // Run test
    RDFFormat resultFormat= descriptionService.returnOutputFormat((DescriptionRequestMessageImpl) message);

    // Check the response
    // It will return JSON because the method cannot/should not accept a String (So, it returns the Default)
    assertThat(resultFormat).isEqualTo(RDFFormat.JSONLD10_COMPACT_PRETTY);
  }

  @Test
  void returnOutputFormat_MapAsParameter()  {
    // Build Map
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", NTRIPLES);

    List<TypedLiteral> acceptList=new ArrayList<>();
    acceptList.add(new TypedLiteral(TURTLE));
    acceptList.add(new TypedLiteral(NTRIPLES));

    // Run test
    RDFFormat resultFormat= descriptionService.returnOutputFormat(headers);

    // Check the response
    assertThat(resultFormat).isEqualTo(RDFFormat.NTRIPLES);
  }

  @Test
  void returnOutputFormat_MapAsParameter_MultipleHeaders()  {
    // Build Map
    Map<String, String> headers = new HashMap<>();
    headers.put("accept", RDF_XML + ", "+ NTRIPLES);

    // Run test
    RDFFormat resultFormat= descriptionService.returnOutputFormat(headers);

    // Check the response
    assertThat(resultFormat).isEqualTo(RDFFormat.NTRIPLES);
  }

  @Test
  void getDepth_DescriptionMessageAsParameter()  {
    // Build Message
    DescriptionRequestMessage message=new DescriptionRequestMessageBuilder()
        ._securityToken_(new DynamicAttributeTokenBuilder()
            ._tokenValue_("Fake Token")
            ._tokenFormat_(TokenFormat.JWT)
            .build())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(URI.create("https://issuerConnector"))
        ._modelVersion_("4.0.2")
        ._senderAgent_(URI.create("https://senderAgent"))
        ._recipientConnector_(URI.create("https://localwslhost:8082/api/ids/data"))
        ._recipientAgent_(URI.create("https://localwslhost:8082/api/ids/data"))
        .build();

    // Run test without DEPTH
    int resultDepth= descriptionService.getDepth((DescriptionRequestMessageImpl) message);

    // Check the response
    assertThat(resultDepth).isEqualTo(0);


    // Set Depth as 3 and rerun test
    message.setProperty(DEPTH, 3);

    // Run test
    resultDepth= descriptionService.getDepth((DescriptionRequestMessageImpl) message);

    // Check the response
    assertThat(resultDepth).isEqualTo(3);
  }

  @Test
  void getDepth_MapAsParameter()  {
    // Build Map
    Map<String, String> headers = new HashMap<>();

    // Run test without DEPTH
    int resultDepth= descriptionService.getDepth(headers);

    // Check the response
    assertThat(resultDepth).isEqualTo(0);

    // Set Depth as 3 and rerun test
    headers.put("depth", "3");
    resultDepth= descriptionService.getDepth(headers);

    // Check the response
    assertThat(resultDepth).isEqualTo(3);
  }

  @Test
  void checkIDSHeaders() {
    // Add mandatory headers
    Map<String, String> headers=new HashMap<>();
    addIDSHeaders(headers);

    // Remove 'ids-securitytoken' from list of Mandatoty Headers and test
    headers.remove("ids-securitytoken");
    Exception exception= assertThrows(BadRequestException.class, () ->descriptionService.checkIDSHeaders(headers));
    assertThat(exception.getMessage()).isEqualTo("ids-securitytoken");

    // Clear list of Headers
    headers.clear();
    addIDSHeaders(headers);

    // Remove 'ids-modelversion' from list of Mandatoty Headers and test
    headers.remove("ids-modelversion");
    exception= assertThrows(BadRequestException.class, () ->descriptionService.checkIDSHeaders(headers));
    assertThat(exception.getMessage()).isEqualTo("ids-modelversion");

    // Clear list of Headers
    headers.clear();
    addIDSHeaders(headers);

    // Remove 'ids-issued' from list of Mandatoty Headers and test
    headers.remove("ids-issued");
    exception= assertThrows(BadRequestException.class, () ->descriptionService.checkIDSHeaders(headers));
    assertThat(exception.getMessage()).isEqualTo("ids-issued");

    // Clear list of Headers
    headers.clear();
    addIDSHeaders(headers);

    // Remove 'ids-senderagent' from list of Mandatoty Headers and test
    headers.remove("ids-senderagent");
    exception= assertThrows(BadRequestException.class, () ->descriptionService.checkIDSHeaders(headers));
    assertThat(exception.getMessage()).isEqualTo("ids-senderagent");

    // Clear list of Headers
    headers.clear();
    addIDSHeaders(headers);

    // Remove 'ids-issuerconnector' from list of Mandatoty Headers and test
    headers.remove("ids-issuerconnector");
    exception= assertThrows(BadRequestException.class, () ->descriptionService.checkIDSHeaders(headers));
    assertThat(exception.getMessage()).isEqualTo("ids-issuerconnector");

    // Clear list of Headers
    headers.clear();
    addIDSHeaders(headers);

  }

  private static void addIDSHeaders(Map<String, String> headers){
    // Add mandatory headers
    headers.put("ids-securitytoken", "FakeToken");
    headers.put("ids-modelversion", "4.0.0");
    headers.put("ids-issued", "2024-09-02T14:04:45.078Z");
    headers.put("ids-senderagent", "https://senderagent");
    headers.put("ids-issuerconnector", "https://issuerconnector");
  }
}