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
package pt.inesctec.ids.broker.util;

import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;

import org.apache.jena.query.Query;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;


/** This class tests the QueryUtil class*/
@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class QueryUtilTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryUtilTest.class);

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
  String graphNameProducer;
  String graphNameSubcontracted;
  String graphNameProducerMultipleCatalogs;

  @BeforeEach
  void setUp() {
    service=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    adminGraphService=new AdminGraphService(accessor);
    testUtil=new TestUtil();
    fusekiConverter=new FusekiConverter();

    LOGGER.info("Cleaning the Admin graph...");
    // Cleaning the Admin graph
    testUtil.removeAdminGraph(accessor);
    adminGraphService.initializeAdminGraph();
    testUtil.removeAllGraphsFromDB(accessor);
    LOGGER.info("... Graphs Cleaned...");


    LOGGER.info("Initialize Connectors...");
    // initialize Connectors
    connectorProducer=testUtil.createConnector();
    connectorSubcontracted=testUtil.createConnectorSubcontracted();
    connectorProducerMultipleCatalogs=testUtil.createConnectorWithMultipleCatalogs();
    LOGGER.info("... Connectors inicialized.");

    LOGGER.info("Initialize Resources...");
    // initialize Resources
    producerResource=testUtil.createResource();
    subcontractedResource=testUtil.createResource2();
    LOGGER.info("... Resource initialized.");

    LOGGER.info("Register Connectors on DB...");
    // Register Connectors on DB
    graphNameProducer =service.addConnectorToFuseki(connectorProducer);
    graphNameSubcontracted=service.addConnectorToFuseki(connectorSubcontracted);
    graphNameProducerMultipleCatalogs=service.addConnectorToFuseki(connectorProducerMultipleCatalogs);
    LOGGER.info("... Connectors registered.");

    /*System.err.println("graphNameProducer: "+graphNameProducer);
    System.err.println("graphNameSubcontracted: "+graphNameSubcontracted);
    System.err.println("graphNameProducerMultipleCatalogs: "+graphNameProducerMultipleCatalogs);*/
    LOGGER.info("Register Resources on DB...");
    // Register Resources on DB
    service.addResourceToFuseki(producerResource, connectorProducer.getId());
    service.addResourceToFuseki(subcontractedResource, connectorSubcontracted.getId());
    LOGGER.info("...Resource registered.");
  }

  /**
   * Tests the constructQuery with a DESCRIBE Query
   * Should return a query
   * */
  @Test
  void constructQuery_describe() {
    String sparqlQuery = "describe ?subject ?predicate ?object WHERE { ?subject  ?predicate  ?object }";
    assertInstanceOf(Query.class, QueryUtil.constructQuery(sparqlQuery));
  }

  /**
   * Tests the constructQuery with an Invalid Operation (DELETE)
   * @return BadRequestException
   * */
  @Test
  void constructQuery_Delete_invalidOperation() {
    String errorMessage="Error parsing query. The query is invalid.";
    String sparqlQuery = "DELETE { ?s ?p ?o . } WHERE { ?s ?p ?o  }";

    Exception exception = assertThrows(BadRequestException.class, () ->
            QueryUtil.constructQuery(sparqlQuery));
    //assertEquals(TestUtil.replaceSeparator(exception.getMessage()), errorMessage.replaceAll(System.lineSeparator(), ""));
  }

  /**
   * Tests the constructQuery with an Invalid Operation
   * @return BadRequestException
   * */
  @Test
  void constructQuery_InvalidSelect_parsingError() {
    String sparqlQuery = "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
            "prefix owl: <http://www.w3.org/2002/07/owl#>\n" +
            "SELECT ?subject ?predicate ?object\n" +
            "WHERE \n" +
            "  ?subject ?predicate ?object\n" +
            "}\n" +
            "LIMIT 25";

    String errorMessage="Error parsing query. The query is invalid.";
    Exception exception = assertThrows(BadRequestException.class, () ->
            QueryUtil.constructQuery(sparqlQuery));
    assertEquals(errorMessage, exception.getMessage());
  }

  /**
   * Tests the constructQuery with an Invalid Operation (INSERT)
   * @return BadRequestException
   * */
  @Test
  void constructQuery_insert_parsingError() {
    String sparqlQuery = "INSERT ?subject ?predicate ?object WHERE {?subject ?predicate ?object} LIMIT 10";
    String errorMessage="Error parsing query. The query is invalid.";

    Exception exception = assertThrows(BadRequestException.class, () ->
            QueryUtil.constructQuery(sparqlQuery));
    assertEquals(errorMessage, exception.getMessage());
  }


  /**
   * Tests the constructQuery with a Valid Operation (SELECT)
   * */
  @Test
  void constructQuery_Select_Valid() {
    String sparqlQuery = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";
    assertInstanceOf(Query.class, QueryUtil.constructQuery(sparqlQuery));
  }

  @Test
  void constructQuery_InValid_NoWhere() {
    String sparqlQuery = "SELECT  ?subject ?predicate ?object";
    String errorMessage="Error parsing query. The query is invalid.";

    Exception exception = assertThrows(BadRequestException.class, () ->
            QueryUtil.constructQuery(sparqlQuery));
    assertEquals(errorMessage, exception.getMessage());
  }

  /**
   * Tests the constructQuery with a Valid Operation (ASK) and No WHERE
   * The ASK is the only operator that does not demand a WHERE
   * */
  @Test
  void constructQuery_Valid_NoWhere_ASK() {
    String sparqlQuery = "PREFIX  owl:  <http://www.w3.org/2002/07/owl#>\n" +
            "ASK { ?x owl:sameAs <https://test-broker.com/id/test/connector-subcontracted> }";

    assertInstanceOf(Query.class, QueryUtil.constructQuery(sparqlQuery));
  }

  @Test
  void rewriteSelect_InvalidQuery_from() {
    String sparqlQuery = "SELECT  ?subject ?predicate ?object FROM NAMED <"+ graphNameProducer +"> WHERE { ?subject  ?predicate  ?object }";
    String errorMessage="Invalid query. A FROM clause is not allowed.";

    Exception exception = assertThrows(BadRequestException.class, () ->
            QueryUtil.rewriteSelect(sparqlQuery, accessor));
    assertEquals(errorMessage, exception.getMessage());
  }

  @Test
  void rewriteSelect_ValidQuery() {
    String sparqlQuery = "SELECT  ?subject ?predicate ?object WHERE { ?subject  ?predicate  ?object }";
    assertInstanceOf(Query.class, QueryUtil.rewriteSelect(sparqlQuery, accessor));
  }

  @Test
  void rewriteQuery_ValidQuery_WhereReserved() {
    String sparqlQuery = "SELECT  ?subject ?predicate ?object WHERE { GRAPH ?__RESERVED { ?subject  ?predicate  ?object }}";

    String expectedResultQuery="SELECT  ?subject ?predicate ?object\n" +
            "FROM NAMED <"+graphNameSubcontracted+">\n" +
            "FROM NAMED <"+graphNameProducerMultipleCatalogs+">\n" +
            "FROM NAMED <"+graphNameProducer+">\n" +
            "WHERE\n" +
            "  { GRAPH ?__RESERVED\n" +
            "      { ?subject  ?predicate  ?object }\n" +
            "  }";

    Query response=QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor);

    assertTrue(CompareUtil.compareStrings(expectedResultQuery, response.toString()));
  }

  @Test
  void rewriteQuery_Where() {
    // Deactivate Producer Connector
    adminGraphService.deactivateConnector(graphNameProducer);
    String sparqlQuery = "SELECT  ?subject ?predicate ?object WHERE { ?subject  ?predicate  ?object }";

    // I'm using variables because the GraphName has a hash that is not always equal
    String expectedResultQuery="SELECT  ?subject ?predicate ?object\n" +
            "FROM NAMED <"+graphNameSubcontracted+">\n" +
            "FROM NAMED <"+graphNameProducerMultipleCatalogs+">\n" +
            "WHERE\n" +
            "  { GRAPH ?__RESERVED\n" +
            "      { ?subject  ?predicate  ?object }\n" +
            "  }";
    System.out.println(QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor));
    assertTrue(CompareUtil.compareStrings(
            expectedResultQuery,
            QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor).toString()));
  }

  @Test
  void rewriteQuery_NoWhere() {
    String sparqlQuery = "SELECT  ?subject ?predicate ?object";
    String resultError="Error parsing query. The query is invalid.";

    Exception exception=assertThrows(BadRequestException.class, () ->
            QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor));
    assertEquals(resultError, exception.getMessage());
  }

  @Test
  void rewriteQuery_BaseConnector() {
    // Deactivate Producer Connector
    adminGraphService.deactivateConnector(graphNameProducer);

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "SELECT ?subject ?predicate ?object\n" +
            "WHERE {\n" +
            "    ?subject a ids:BaseConnector .\n" +
            "    ?subject ?predicate ?object \n" +
            "}";

    // I'm using variables because the GraphName has a hash that is not always equal
    String expectedResultQuery="PREFIX ids: <https://w3id.org/idsa/core/>\n\n" +
            "SELECT  ?subject ?predicate ?object\n" +
            "FROM NAMED <"+graphNameSubcontracted+">\n" +
            "FROM NAMED <"+graphNameProducerMultipleCatalogs+">\n" +
            "WHERE\n" +
            "  { GRAPH ?__RESERVED\n" +
            "      { ?subject a ids:BaseConnector ;\n" +
            "         ?predicate ?object\n" +
            "  } }";


    String rewrittenQuery=QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor).toString();
    System.out.println(rewrittenQuery);

    assertTrue(CompareUtil.compareStrings(
            TestUtil.replaceBlankSpacesWithOneSpace(expectedResultQuery),
            TestUtil.replaceBlankSpacesWithOneSpace(rewrittenQuery)));
  }

  @Test
  void rewriteQuery_EmptyActiveGraphs() {
    // Deactivate All Connectors
    adminGraphService.deactivateConnector(graphNameProducer);
    adminGraphService.deactivateConnector(graphNameProducerMultipleCatalogs);
    adminGraphService.deactivateConnector(graphNameSubcontracted);

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>\n" +
            "SELECT ?subject ?predicate ?object\n" +
            "WHERE {\n" +
            "    ?subject a ids:BaseConnector .\n" +
            "    ?subject ?predicate ?object \n" +
            "}";

    assertNull(QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor));
  }

  @Test
  void rewriteConstruct_ValidQuery() {
    String expectedResult = "CONSTRUCT { ?subject ?predicate ?object . }\n"+
            "FROM NAMED <"+graphNameSubcontracted+">\n" +
            "WHERE { GRAPH ?__RESERVED { ?subject  ?predicate  ?object } }";

    String sparqlQuery = "CONSTRUCT { ?subject ?predicate ?object}\n"+
            "WHERE { ?subject  ?predicate  ?object }";

    // Deactivate Connector
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());

    Query resultQuery=QueryUtil.rewriteConstructAndDescribe(sparqlQuery, accessor);
    assertInstanceOf(Query.class, resultQuery);
    assertEquals(TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(expectedResult)),
            TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(resultQuery.toString())));
  }

  @Test
  void rewriteDescribe_ValidQuery() {
    String expectedResult = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "DESCRIBE ?x" +
            "FROM NAMED <"+graphNameSubcontracted+">" +
            "WHERE { GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> } }";

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>" +
            "DESCRIBE ?x " +
            "WHERE  {  ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }";

    // Deactivate Connector
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());

    Query resultQuery=QueryUtil.rewriteConstructAndDescribe(sparqlQuery, accessor);
    assertInstanceOf(Query.class, resultQuery);
    assertEquals(TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(expectedResult)),
            TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(resultQuery.toString())));
  }

  @Test
  void rewriteDescribe_InvalidQuery() {
    // Is invalid because it does not have the prefix
    String sparqlQuery = "DESCRIBE ?x \n" +
            "            WHERE  {  GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }}";

    // Deactivate Connector
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());

    String resultError="Error parsing query. The query is invalid.";

    Exception exception=assertThrows(BadRequestException.class, () ->
            QueryUtil.rewriteQuery(QueryUtil.constructQuery(sparqlQuery), accessor));
    assertEquals(resultError, exception.getMessage());
  }

  /**
   * Tests the rewriteASK with a query that does not have a WHERE clause
   * The WHERE shoud be added
   * */
  @Test
  void rewriteASK_NoWhere() {
    // Deactivate Connector - This way I only have on active graph
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());


    // No WHERE -> Should PASS
    String expectedResult = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "ASKFROM NAMED <"+graphNameSubcontracted+">" +
            "WHERE { GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> } }";

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
            "ASK {  ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }";
    Query resultQuery=QueryUtil.rewriteASK(sparqlQuery, accessor);
    assertInstanceOf(Query.class, resultQuery);
    System.out.println(TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(resultQuery.toString())));
    assertEquals(TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(expectedResult)),
            TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(resultQuery.toString())));
  }

  /**
   * Tests the rewriteASK with a dataset that does not have any active connectors
   * Should return null
   * */
  @Test
  void rewriteASK_NoActiveGraphs() {
    // Deactivate Connector - This way I only have on active graph
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());
    service.makeConnectorUnavailable(connectorSubcontracted.getId());


    // No active graphs-> Should PASS
    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
            "ASK {  ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }";

    assertNull(QueryUtil.rewriteASK(sparqlQuery, accessor));
  }

  /**
   * Tests the rewriteASK with a query that already has a WHERE clause with GRAPH ?__RESERVED
   * Should Pass. The code skips adding the GRAPH ?__RESERVED
   * */
  @Test
  void rewriteASK_AlreadyHasGraphReserved_ShouldPass() {
    // WHERE that already has the with GRAPH ?__RESERVED clause -> Should PASS
    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "ASK WHERE { GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> } }";

    String expectedResultQuery="PREFIX  owl:  <http://www.w3.org/2002/07/owl#>\n\n" +
            "ASK\n" +
            "FROM NAMED <"+graphNameSubcontracted+">\n" +
            "FROM NAMED <"+graphNameProducerMultipleCatalogs+">\n" +
            "FROM NAMED <"+graphNameProducer+">\n" +
            "WHERE\n" +
            "  { GRAPH ?__RESERVED\n" +
            "      { ?x  owl:sameAs  <"+connectorSubcontracted.getId()+"> }\n" +
            "  }";

    Query response=QueryUtil.rewriteASK(sparqlQuery, accessor);
    System.out.println(response);
    assertTrue(CompareUtil.compareStrings(
            expectedResultQuery,
            response.toString()));

  }

  /**
   * Tests the rewriteASK with a query that already has a FROM clause
   * Should Fail
   * */
  @Test
  void rewriteASK_AlreadyAsFROM_ShouldFail() {
    // Deactivate Connector - This way I only have on active graph
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());

    // WHERE that already has the with GRAPH ?__RESERVED clause -> Should FAIL
    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "ASK FROM NAMED <"+graphNameSubcontracted+">" +
            "WHERE { GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }  }";

    String resultError="Invalid query. A FROM clause is not allowed.";

    Exception exception=assertThrows(BadRequestException.class, () ->
            QueryUtil.rewriteASK(sparqlQuery, accessor));
    assertEquals(resultError, exception.getMessage());

  }

  @Test
  void rewriteASK_ValidQuery() {
    // Deactivate Connector
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());
    service.makeConnectorUnavailable(connectorProducer.getId());

    String expectedResult = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "ASKFROM NAMED <"+graphNameSubcontracted+">" +
            "WHERE { GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> } }";

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
            "ASK WHERE  {  ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }";

    Query resultQuery=QueryUtil.rewriteConstructAndDescribe(sparqlQuery, accessor);
    assertInstanceOf(Query.class, resultQuery);
    assertEquals(TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(expectedResult)),
            TestUtil.replaceBlankSpacesWithOneSpace(TestUtil.replaceSeparator(resultQuery.toString())));
  }

  /**
   * Tests the rewrite with no active Connectors
   * Should return null
   * */
  @Test
  void executeSPARQLQuery_WithNoActiveConnectors_DESCRIBE_CONSTRUCT_ASK_SELECT() {

    // Disable Connectors
    service.makeConnectorUnavailable(connectorProducer.getId());
    service.makeConnectorUnavailable(connectorSubcontracted.getId());
    service.makeConnectorUnavailable(connectorProducerMultipleCatalogs.getId());

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "DESCRIBE ?x\n" +
            "WHERE  {  ?x ids:keyType idsc:RSA }";
    assertNull(QueryUtil.rewriteConstructAndDescribe(sparqlQuery, accessor));

    sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "ASK WHERE  {  ?x ids:keyType idsc:RSA }";
    assertNull(QueryUtil.rewriteASK(sparqlQuery, accessor));

    sparqlQuery = "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject  ?predicate  ?object }";;
    assertNull(QueryUtil.rewriteConstructAndDescribe(sparqlQuery, accessor));

    sparqlQuery = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";
    assertNull(QueryUtil.rewriteSelect(sparqlQuery, accessor));

  }

}