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

import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.Resource;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.util.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.FusekiConstants.CONNECTORS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ModelServiceTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelServiceTest.class);
  @Autowired
  FusekiConnectionAccessor accessor;

  @Autowired @Qualifier("catalogURI")
  private String catalogUri;

  TestUtil testUtil;

  ModelService service;
  BrokerService brokerService;

  FusekiConverter fusekiConverter;

  Connector connectorProducer;
  Connector connectorSubcontracted;
  Connector connectorProducerMultipleCatalogs;


  @BeforeEach
  void setUp()  {
    service=new ModelService(accessor);
    brokerService=new BrokerService(accessor);

    //service.uploadData();
    testUtil=new TestUtil();
    fusekiConverter=new FusekiConverter();

    connectorProducer=testUtil.createConnector();
    connectorSubcontracted=testUtil.createConnectorSubcontracted();
    connectorProducerMultipleCatalogs=testUtil.createConnectorWithMultipleCatalogs();
  }

  @Test
  void listAllGraphs() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);


    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);

    assertEquals(service.listAllGraphs().size(), 3);
  }

  @Test
  void listAllGraphsExceptAdmin() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);

    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);

    assertEquals(service.listAllGraphs().size(), 3);
    assertEquals(service.listAllGraphsExceptAdmin().size(), 2);
  }

  @Test
  void listGraphsUsingKeyword() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);


    brokerService.addConnectorToFuseki(connectorProducer);

    assertEquals(service.listGraphsUsingKeyword(CONNECTORS).size(), 1);
    assertEquals(service.listGraphsUsingKeyword("broker").size(), 0);
    assertEquals(service.listGraphsUsingKeyword("outra").size(), 0);
    assertEquals(service.listGraphsUsingKeyword(GraphNameUtil.getConnectorUniqueIDFromConnectorURL(connectorProducer.getId())).size(), 1);
  }

  @Test
  void getListOfConnectorsID() {
    List<String> resultList=new ArrayList<>();
    resultList.add("https://test-broker.com/id/test/connector-producer");
    resultList.add("https://test-broker.com/id/test/connector-subcontracted");
    resultList.add("https://test-broker.com/id/test/multipleCatalogs/connector-producer");

    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);


    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);

    service.getListOfConnectorsID().forEach(System.out::println);
    resultList.forEach(System.out::println);

    assertEquals(service.getListOfConnectorsID().size(), 3);
    assertTrue(CompareUtil.compareStrings(service.getListOfConnectorsID(), resultList));
  }


  @Test
  void getModelWithConnectorIDEqualTo() {
    String result="@prefix ids:  <https://w3id.org/idsa/core/> .\n" +
        "@prefix idsc: <https://w3id.org/idsa/code/> .\n" +
        "@prefix owl:  <http://www.w3.org/2002/07/owl#> .\n" +
        "@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns> .\n" +
        "\n" +
        "<http://test-hostname:8082/connectors/connector-producer/-846787637>\n" +
        "        a                         ids:BaseConnector ;\n" +
        "        owl:sameAs                <https://test-broker.com/id/test/connector-producer> ;\n" +
        "        ids:curator               <https://www.inesctec.pt/> ;\n" +
        "        ids:description           \"Description\" ;\n" +
        "        ids:hasDefaultEndpoint    <http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807> ;\n" +
        "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
        "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
        "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
        "        ids:publicKey             <http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891> ;\n" +
        "        ids:resourceCatalog       <http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664> ;\n" +
        "        ids:securityProfile       idsc:BASE_SECURITY_PROFILE ;\n" +
        "        ids:title                 \"Title\" .\n" +
        "\n" +
        "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\n" +
        "        a              ids:ConnectorEndpoint ;\n" +
        "        owl:sameAs     <https://test-broker.com/id/test/connectorEndpoint/dhiasdcna> ;\n" +
        "        ids:accessURL  <https://host.docker.internal:8080/test/connector/api/ids/data> .\n" +
        "\n" +
        "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\n" +
        "        a             ids:PublicKey ;\n" +
        "        owl:sameAs    <https://test-broker.com/id/test/publicKey/dhiasdcna> ;\n" +
        "        ids:keyType   idsc:RSA ;\n" +
        "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
        "\n" +
        "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\n" +
        "        a           ids:ResourceCatalog ;\n" +
        "        owl:sameAs  <https://test-broker.com/id/test/catalog/api/connector-producer> .\n";

    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);

    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);

    Optional<ModelDto> modelDto=service.getModelWithConnectorIDEqualTo(connectorProducer.getId());

    System.out.println(fusekiConverter.modelToTurleFormat(modelDto.get().getModel()));
    assertTrue(modelDto.isPresent());
    assertEquals(fusekiConverter.modelToTurleFormat(modelDto.get().getModel()).toString(),
        result);
    assertTrue(
            CompareUtil.compareStrings(
                    fusekiConverter.modelToTurleFormat(modelDto.get().getModel()).toString().replaceAll("\\R", "\n"),
                    result.replaceAll("\\R", "\n")));

  }

  @Test
  void convertConnectorIDToGraphName() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);


    String connectorModelName=brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);

    Optional<String> graphName=service.convertConnectorIDToGraphName(connectorProducer.getId());

    assertTrue(graphName.isPresent());
    assertEquals(graphName.get().toString(), connectorModelName);

  }

  @Test
  void convertGraphName2ConnectorID() {
    // Assert that the BD is empty
    service.removeAllData();
    /*assertEquals(service.listAllGraphsExceptAdmin().size(), 0);*/


    String connectorModelName=brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);

    System.out.println(connectorModelName);
    String connectorID=service.convertGraphName2ConnectorID(URI.create(connectorModelName));

    assertEquals(connectorID.toString(), connectorProducer.getId().toString());
  }

  @Test
  void removeAllData() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);

    Connector conn= testUtil.createConnector();

    // Add 2 connectors and 1 broker
    brokerService.addConnectorToFuseki(conn);
    brokerService.addConnectorToFuseki(connectorSubcontracted);


    // Remove all Data
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);
  }


  @Test
  void removeConnectorGraphs() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);
    Connector conn= testUtil.createConnector();

    // Add 2 connectors and 1 broker
    brokerService.addConnectorToFuseki(conn);
    brokerService.addConnectorToFuseki(connectorSubcontracted);


    // Remove all connectors
    service.removeConnectorGraphs();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0, "List should be empty");
  }

  @Test
  void removeConnectorGraph() {

    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);
    Connector conn= testUtil.createConnector();

    // Add 2 connectors and one broker
    String graphName=brokerService.addConnectorToFuseki(conn);
    brokerService.addConnectorToFuseki(connectorSubcontracted);

    assertEquals(service.listAllGraphsExceptAdmin().size(), 2);
    // Remove one connector
    // Assert that the BD only has one entry
    service.removeConnectorGraph(graphName);
    assertEquals(service.listAllGraphsExceptAdmin().size(), 1, "List should have one connector");
  }

  @Test
  void getModelAndItsChildren_Statement() {
    String expectedModelToRemove="[https://localhost:8080/iANAMediaType/cnasoe0, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/IANAMediaType]\n" +
            "[https://localhost:8080/iANAMediaType/cnasoe0, https://w3id.org/idsa/core/filenameExtension, \"application/octet-stream\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Artifact]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/artifacts/xdweudnja-e1af-46a1-9f42-384hnd]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366, https://w3id.org/idsa/core/byteSize, \"1\"^^http://www.w3.org/2001/XMLSchema#integer]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366, https://w3id.org/idsa/core/checkSum, \"0\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366, https://w3id.org/idsa/core/creationDate, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366, https://w3id.org/idsa/core/fileName, \"file3\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/representations/ab2a6919-c7da-4df2-9a2a-9a508beea249]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/created, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Representation]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/instance, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/mediaType, https://localhost:8080/iANAMediaType/cnasoe0]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/language, https://w3id.org/idsa/code/EN]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/instance, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/modified, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/instance, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/624556366]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632, https://w3id.org/idsa/core/instance, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Permission]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913, https://w3id.org/idsa/core/action, https://w3id.org/idsa/code/USE]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913, https://w3id.org/idsa/core/description, \"PROVIDE_ACCESS\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913, https://w3id.org/idsa/core/title, \"PROVIDE_ACCESS\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Artifact]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726, https://w3id.org/idsa/core/byteSize, \"0\"^^http://www.w3.org/2001/XMLSchema#integer]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726, https://w3id.org/idsa/core/checkSum, \"0\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726, https://w3id.org/idsa/core/creationDate, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/939358726, https://w3id.org/idsa/core/fileName, \"test-file\"]\n" +
            "[https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ConnectorEndpoint]\n" +
            "[https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2, https://w3id.org/idsa/core/endpointDocumentation, https://url.for.documentation]\n" +
            "[https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2, https://w3id.org/idsa/core/accessURL, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Artifact]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/artifacts/dweytgw76da-e1af-46a1-9f42-cnasoe0]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031, https://w3id.org/idsa/core/byteSize, \"4\"^^http://www.w3.org/2001/XMLSchema#integer]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031, https://w3id.org/idsa/core/checkSum, \"0\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031, https://w3id.org/idsa/core/creationDate, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/1595376031, https://w3id.org/idsa/core/fileName, \"file4\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195, https://w3id.org/idsa/core/offeredResource, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Artifact]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/artifacts/cduerfha9s-e1af-46a1-9f42-ndkfhcne8endaj]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208, https://w3id.org/idsa/core/byteSize, \"2\"^^http://www.w3.org/2001/XMLSchema#integer]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208, https://w3id.org/idsa/core/checkSum, \"0\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208, https://w3id.org/idsa/core/creationDate, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632/47212208, https://w3id.org/idsa/core/fileName, \"file2\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ContractOffer]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/contracts/30481515-3fe0-459e-a08b-57cf17204d0f]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807, https://w3id.org/idsa/core/contractDate, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807, https://w3id.org/idsa/core/contractEnd, \"2025-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807, https://w3id.org/idsa/core/contractStart, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807, https://w3id.org/idsa/core/permission, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807/-1474186913]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/title, \"PDP-P20230303\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/keyword, \"term1\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/resourceEndpoint, https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/contractOffer, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1053123807]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/language, https://w3id.org/idsa/code/EN]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/created, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/version, \"1\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/description, \"Information about product P20230303\"]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/representation, http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692/1564849632]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Resource]\n" +
            "[http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692, https://w3id.org/idsa/core/modified, \"2022-03-23T12:23:22Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]";
    // Creates a list using the \n as the separator for the rows
    ArrayList<String> expectedModelToRemoveAsList = new ArrayList<>(Arrays.asList(expectedModelToRemove.split("\n")));

    Model expectedModel= fusekiConverter.convertStringsToRDFModel(expectedModelToRemoveAsList);


    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);

    Resource resource=testUtil.createResourceWithMultipleArtefacts();

    // Add Connector To Fuseki
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);


    // Add Resource to Fuseki
    brokerService.addResourceToFuseki(resource, connectorProducerMultipleCatalogs.getId());

    ModelDto connectorModel=service.getModelWithConnectorIDEqualTo(connectorProducerMultipleCatalogs.getId()).get();


    // Sees if the Offer exists
    LOGGER.info("Sees if the Offer exists");
    Optional<Statement> resourceOptinal=connectorModel.getModel().listStatements()
            .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(resource.getId().toString()))).nextOptional(); // Uses the SameAs

    LOGGER.info("Get model to remove");
    Model modelToRemove= service.getModelAndItsChildren(resourceOptinal.get(), connectorModel);

    LOGGER.info("Assert That the modelToRemove is the same size as the expectedModelToRemoveAsList");
    assertEquals(modelToRemove.listStatements().toList().size(), expectedModelToRemoveAsList.size());

    System.out.println(FusekiUtil.getStatementsDiff(modelToRemove, expectedModel));

    assertFalse(FusekiUtil.getStatementsDiff(modelToRemove, expectedModel).hasDiff());
  }

  @Test
  void getResourceModelFromDB() {
    ModelService service=new ModelService(accessor);

    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);

    Resource resource=testUtil.createResourceWithMultipleArtefacts();

    // Add Connector To Fuseki
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);
    ModelDto connectorModel_BeforeAddingResource=service.getModelWithConnectorIDEqualTo(connectorProducerMultipleCatalogs.getId()).get();

    // Add Resource to Fuseki
    brokerService.addResourceToFuseki(resource, connectorProducerMultipleCatalogs.getId());

    ModelDto connectorModel=service.getModelWithConnectorIDEqualTo(connectorProducerMultipleCatalogs.getId()).get();

    Model resourceModelOnDB= service.getModelAndItsChildren(resource.getId(), connectorModel);

    System.out.println(fusekiConverter.modelToTurleFormat(resourceModelOnDB));
    // Assert that the ResourceModel+ConnectorModel Before Resource + 1 (Catalog triple) is equal to
    // the Total size
    assertThat(connectorModel_BeforeAddingResource.getModel().listStatements().toList().size()
            + resourceModelOnDB.listStatements().toList().size()+1)
            .isEqualTo(connectorModel.getModel().listStatements().toList().size());
  }

  @Test
  void getConnectorCatalog() {
    // Assert that the BD is empty
    service.removeAllData();
    assertEquals(service.listAllGraphsExceptAdmin().size(), 0);
    // Add Connectors to Fuseki
    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(connectorProducerMultipleCatalogs);

    List<Connector> activeConnectors=service.getConnectorCatalog();
    assertThat(activeConnectors.size()).isEqualTo(3);

    // Make one Connector Anavailable
    brokerService.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    activeConnectors=service.getConnectorCatalog();
    assertThat(activeConnectors.size()).isEqualTo(2);

    System.out.println("Printing List of Active Connectors");
    //activeConnectors.forEach(System.out::println);

  }

}