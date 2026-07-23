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

import de.fraunhofer.iais.eis.Broker;
import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.Resource;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.util.CompareUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This Test Class retrieves various queries using the Query Method
 * */
@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ExecuteQueryBrokerServiceTest {

  @Autowired
  FusekiConnectionAccessor accessor;

  BrokerService service;
  ModelService modelService;
  AdminGraphService adminGraphService;
  TestUtil testUtil;
  Connector connectorProducer;
  Connector connectorSubcontracted;
  Connector connectorProducerMultipleCatalogs;
  Resource producerResource;
  Resource subcontractedResource;

  FusekiConverter fusekiConverter;
  String graphName;

  @BeforeEach
  void setUp() {
    service=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    adminGraphService=new AdminGraphService(accessor);
    testUtil=new TestUtil();
    fusekiConverter=new FusekiConverter();

    // REMOVE ALL
    testUtil.removeAllGraphsFromDB(accessor);
    testUtil.removeAdminGraph(accessor);
    adminGraphService.initializeAdminGraph();

    // initialize Connectors
    connectorProducer=testUtil.createConnector();
    connectorSubcontracted=testUtil.createConnectorSubcontracted();
    connectorProducerMultipleCatalogs=testUtil.createConnectorWithMultipleCatalogs();

    // initialize Resources
    producerResource=testUtil.createResource();
    subcontractedResource=testUtil.createResource2();

    // Register Connectors on DB
    graphName=service.addConnectorToFuseki(connectorProducer);
    service.addConnectorToFuseki(connectorSubcontracted);
    service.addConnectorToFuseki(connectorProducerMultipleCatalogs);

    // Register Resources on DB
    service.addResourceToFuseki(producerResource, connectorProducer.getId());
    service.addResourceToFuseki(subcontractedResource, connectorSubcontracted.getId());
  }

  @Test
  void handleSelectQuery_executeBasicSPARQLQuery() {
    String sparqlQuery = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";

    String result= service.executeSPARQLQuery(sparqlQuery);

    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 151);

        /*
     * SELECT  ?subject ?predicate ?object
FROM NAMED <http://localhost:3031/connectors/connector-producer/-1853907381>
FROM NAMED <http://localhost:3031/connectors/connector-subcontracted/-439788327>
FROM NAMED <http://localhost:3031/connectors/connector-producer/989046453>
WHERE
  { GRAPH ?__RESERVED
      { ?subject  ?predicate  ?object }
  }
     * */
  }

  @Test
  void handleSelectQuery_executeConnectorSPARQLQuery() {
    String expectResult="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-subcontracted>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/title>\t\"Title\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/multipleCatalogs/connector-producer>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/title>\t\"Title\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-producer>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/title>\t\"Title\"\n";
    ArrayList<String> expectedResultAsList = new ArrayList<>(Arrays.asList(expectResult.split("\n")));

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "\tSELECT ?subject ?predicate ?object\n" +
            "\tWHERE {\n" +
            "    \t\t?subject a ids:BaseConnector .\n" +
            "    \t\t?subject ?predicate ?object \n" +
            "\t}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 38);
    // The assertation is made this way because the order may difer
    assertTrue(CompareUtil.compareStrings(resultAsList, expectedResultAsList));
  }

  @Test
  void handleSelectQuery_executeArtefactsSPARQLQuery() {
    String expectResult="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/Artifact>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<https://w3id.org/idsa/core/byteSize>\t0\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<https://w3id.org/idsa/core/checkSum>\t\"0\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<https://w3id.org/idsa/core/creationDate>\t\"2022-03-23T12:23:22Z\"^^<http://www.w3.org/2001/XMLSchema#dateTimeStamp>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<https://w3id.org/idsa/core/fileName>\t\"test-file\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/Artifact>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://localhost:8080/localconnector/api/artifacts/resource01>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<https://w3id.org/idsa/core/byteSize>\t0\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<https://w3id.org/idsa/core/checkSum>\t\"0\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<https://w3id.org/idsa/core/creationDate>\t\"2022-03-23T12:23:22Z\"^^<http://www.w3.org/2001/XMLSchema#dateTimeStamp>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<https://w3id.org/idsa/core/fileName>\t\"test-file\"";
    ArrayList<String> expectedResultAsList = new ArrayList<>(Arrays.asList(expectResult.split("\n")));

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "\tSELECT ?subject ?predicate ?object\n" +
            "\tWHERE {\n" +
            "    \t\t?subject a ids:Artifact .\n" +
            "    \t\t?subject ?predicate ?object \n" +
            "\t}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 13);
    // The assertation is made this way because the order may difer
    assertTrue(CompareUtil.compareStrings(resultAsList, expectedResultAsList));

  }

  @Test
  void handleSelectQuery_executeResorceSPARQLQuery() {
    String expectResult="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/description>\t\"Information about product P20230303\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/Resource>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/created>\t\"2022-03-23T12:23:22Z\"^^<http://www.w3.org/2001/XMLSchema#dateTimeStamp>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/language>\t<https://w3id.org/idsa/code/EN>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/modified>\t\"2022-03-23T12:23:22Z\"^^<http://www.w3.org/2001/XMLSchema#dateTimeStamp>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/keyword>\t\"term1\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/resourceEndpoint>\t<https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/contractOffer>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1053123807>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/version>\t\"1\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/representation>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230309\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/description>\t\"Information about product P20230309\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/Resource>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://localhost:8080/localconnector/api/offers/resource01>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/created>\t\"2022-03-23T12:23:22Z\"^^<http://www.w3.org/2001/XMLSchema#dateTimeStamp>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/language>\t<https://w3id.org/idsa/code/EN>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/modified>\t\"2022-03-23T12:23:22Z\"^^<http://www.w3.org/2001/XMLSchema#dateTimeStamp>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/keyword>\t\"term1\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/resourceEndpoint>\t<https://w3id.org/idsa/autogen/connectorEndpoint/resource01>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/contractOffer>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/-1769526722>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/version>\t\"1\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/representation>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131>";

    ArrayList<String> expectedResultAsList = new ArrayList<>(Arrays.asList(expectResult.split("\n")));

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "\tSELECT ?subject ?predicate ?object\n" +
            "\tWHERE {\n" +
            "    \t\t?subject a ids:Resource .\n" +
            "    \t\t?subject ?predicate ?object \n" +
            "\t}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 25);
    // The assertation is made this way because the order may difer
    assertTrue(resultAsList.containsAll(expectedResultAsList));

  }

  @Test
  void handleSelectQuery_getResourcesByTitle() {
    List<String> expectedResult=new ArrayList<>();
    expectedResult.add("?subject\t?predicate\t?object");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230309\"");


    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "\tSELECT ?subject ?predicate ?object\n" +
            "\tWHERE {\n" +
            "    \t\t?subject a ids:Resource.\n" +
            "    \t\t?subject ?predicate ?object \n" +
            "                FILTER (regex(str(?predicate), 'title','i'))\n" +
            "\n" +
            "\t}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    // It is expecting 3, because I have to count the header row
    assertEquals(resultAsList.size(), 3);
    // The assertation is made this way because the order may difer
    assertTrue(CompareUtil.compareStrings(resultAsList, expectedResult));
  }

  @Test
  void getResourcesWithTitleEqualTo() {
    List<String> expectedResult=new ArrayList<>();
    expectedResult.add("?subject\t?predicate\t?object");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"");

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "    SELECT ?subject ?predicate ?object\n" +
            "    WHERE {\n" +
            "    \t?subject a ids:Resource.\n" +
            "    \t?subject ?predicate ?object \n" +
            "        FILTER (regex(str(?predicate), 'title','i'))\n" +
            "        FILTER (regex(str(?object ), 'P20230303','i'))\n" +
            "    }";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 2);
    // The assertation is made this way because the order may difer
    assertTrue(CompareUtil.compareStrings(resultAsList, expectedResult));

  }

  @Test
  void artefactsWithFilenameEqualTo() {
    List<String> expectedResult=new ArrayList<>();
    expectedResult.add("?subject\t?predicate\t?object");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692/1564849632/939358726>\t<https://w3id.org/idsa/core/fileName>\t\"test-file\"");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158/1024692131/665234814>\t<https://w3id.org/idsa/core/fileName>\t\"test-file\"");

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "SELECT ?subject ?predicate ?object\n" +
            "WHERE {\n" +
            "    \t?subject a ids:Artifact.\n" +
            "    \t?subject ?predicate ?object \n" +
            "        FILTER (regex(str(?predicate), 'fileName','i'))\n" +
            "        FILTER (regex(str(?object ), 'test-file','i')).\n" +
            "}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 3);
    // The assertation is made this way because the order may difer
    assertTrue(resultAsList.containsAll(expectedResult));

  }

  @Test
  void getAllConnectors() {
    List<String> expectedResult=new ArrayList<>();
    expectedResult.add("?subject\t?predicate\t?object");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/multipleCatalogs/connector-producer>");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-producer>");
    expectedResult.add("<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-subcontracted>");


    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "\tSELECT ?subject ?predicate ?object\n" +
            "\tWHERE {\n" +
            "    \t\t?subject a ids:BaseConnector.\n" +
            "    \t\t?subject ?predicate ?object  \n" +
            "FILTER (regex(str(?predicate), 'owl#sameAs','i'))\n" +
            "\t}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 4);
    // The assertation is made this way because the order may difer
    assertTrue(resultAsList.containsAll(expectedResult));

  }

  @Test
  void getSelfDescriptionOfAConnector() {
    String expectResult="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-subcontracted>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950>\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\t<https://w3id.org/idsa/core/title>\t\"Title\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/multipleCatalogs/connector-producer>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\t<https://w3id.org/idsa/core/title>\t\"Title\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-producer>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/title>\t\"Title\"";
    ArrayList<String> expectedResultAsList = new ArrayList<>(Arrays.asList(expectResult.split("\n")));

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "\tSELECT ?subject ?predicate ?object\n" +
            "\tWHERE {\n" +
            "    \t\t?subject a ids:BaseConnector.\n" +
            "    \t\t?subject ?predicate ?object  \n" +
            "\t}";
    String result=service.executeSPARQLQuery(sparqlQuery);
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 38);
    // The assertation is made this way because the order may difer
    assertTrue(CompareUtil.compareStrings(resultAsList, expectedResultAsList));

  }
}