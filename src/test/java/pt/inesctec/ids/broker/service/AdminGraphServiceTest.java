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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class AdminGraphServiceTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminGraphServiceTest.class);
  @Autowired
  FusekiConnectionAccessor accessor;

  URI graphName;

  TestUtil testUtil;

  AdminGraphService adminGraphService;
  BrokerService brokerService;
  ModelService modelService;

  FusekiConverter fusekiConverter;

  Connector connectorProducer;
  Connector connectorSubcontracted;
  Connector connectorProducerMultipleCatalogs;


  @BeforeEach
  void setUp(){
    modelService=new ModelService(accessor);
    brokerService=new BrokerService(accessor);
    adminGraphService=new AdminGraphService(accessor);

    testUtil=new TestUtil();
    fusekiConverter=new FusekiConverter();

    connectorProducer=testUtil.createConnector();
    connectorSubcontracted=testUtil.createConnectorSubcontracted();
    connectorProducerMultipleCatalogs=testUtil.createConnectorWithMultipleCatalogs();

    // Get Connector Graph Name
    graphName=URI.create("https://inesctec.pt/unit-tests/01");
  }

  @AfterEach
  void tearDown() {
   // testUtil.removeAllGraphsFromDB(accessor);
    // Removing the Admin graph
    testUtil.removeAdminGraph(accessor);
    adminGraphService.initializeAdminGraph();
  }


  @Test
  void addConnector() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphs().size(), 1);

    // Add Connector
    adminGraphService.addConnector(graphName);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();

    adminGraph.listStatementsWithSubjectEqualTo(graphName).forEach(System.out::println);
    //Check if the Connector was added to the Admin Graph
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(graphName).size(), 1);
  }

  @Test
  void checkIfGraphIsActive() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphs().size(), 1);

    // Add Connector to Admin Graph
    adminGraphService.addConnector(graphName);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();

    //Check if the Connector was only added once
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(graphName).size(), 1);
    //Check if the Connector is active
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(graphName).stream().findFirst().get().getObject().asLiteral().getBoolean(), true);

    adminGraphService.deactivateConnector(graphName.toString());
    // GET Admin Graph again (because the graph was updated)
    adminGraph=adminGraphService.getGraph();
    //Check if the Connector is inactive
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(graphName).stream().findFirst().get().getObject().asLiteral().getBoolean(), false);
  }

  @Test
  void initializeAdminGraph() {
    testUtil.removeAllGraphsFromDB(accessor);
    // Check if the Admin Graph is the only on the DB
    assertEquals(modelService.listAllGraphs().size(), 1);

    // After initializing the Graph, it should do nothing
    adminGraphService.initializeAdminGraph();
    assertEquals(modelService.listAllGraphs().size(), 1);


    // Removing the Admin graph
    testUtil.removeAdminGraph(accessor);
    assertEquals(modelService.listAllGraphs().size(), 0);


    // After initializing the Graph again, now it should be 1
    adminGraphService.initializeAdminGraph();
    assertEquals(modelService.listAllGraphs().size(), 1);

    //adminGraphService.getGraph().getModel().write(System.err, "TURTLE");

  }

  @Test
  void deactivateConnectorOnAdminGraph() {

    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    // Add Connector
    adminGraphService.addConnector(graphName);

    // "Remove" the Connector
    adminGraphService.deactivateConnector(String.valueOf(graphName));

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();

    //Check if the Connector is inactive
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(graphName).stream().findFirst().get().getObject().asLiteral().getBoolean(), false);

  }

  @Test
  void activateConnectorOnAdminGraph() {

    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    // Add Connector
    adminGraphService.addConnector(graphName);

    // "Remove" the Connector
    adminGraphService.deactivateConnector(String.valueOf(graphName));

    // Activate the Connector
    adminGraphService.activateConnector(String.valueOf(graphName));

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();

    System.out.println(fusekiConverter.modelToTurleFormat(adminGraph.getModel()));

    //Check if the Connector is active
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(graphName).stream().findFirst().get().getObject().asLiteral().getBoolean(), true);

  }

  @Test
  void getListOfActiveConnectors() {
    List<String> activeGraphsExpected=new ArrayList<>();
    activeGraphsExpected.add("https://inesctec.pt/unit-tests/03");
    activeGraphsExpected.add("https://inesctec.pt/unit-tests/02");

    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    adminGraphService.getGraph().getModel().listStatements().forEachRemaining(System.out::println);

    // Add Connector
    adminGraphService.addConnector(graphName);
    adminGraphService.addConnector(URI.create("https://inesctec.pt/unit-tests/02"));
    adminGraphService.addConnector(URI.create("https://inesctec.pt/unit-tests/03"));

    // "Remove" the Connector
    adminGraphService.deactivateConnector(String.valueOf(graphName));

    // GET Admin Graph
    List<String> activeGraphs=adminGraphService.getListOfActiveConnectors();

    //Check the List of Active COnnectors is what is supposed to be
    assertEquals(activeGraphsExpected, activeGraphs);

  }
}