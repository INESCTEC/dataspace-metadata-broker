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
package pt.inesctec.ids.broker.dto;

import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.Resource;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.JenaService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ModelDtoTest {
  BrokerService service;
  ModelService modelService;
  JenaService jenaService;
  FusekiConverter converter=new FusekiConverter();
  TestUtil testUtil=new TestUtil();

  @Autowired
  FusekiConnectionAccessor accessor;
  Model producerModel = ModelFactory.createDefaultModel();

  FusekiConverter fusekiConverter=new FusekiConverter();
  Connector connector;

  @BeforeEach
  void setUp() {
    service=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    jenaService=new JenaService(accessor);

    testUtil.removeAllGraphsFromDB(accessor);

    // Create a Connector
    connector=testUtil.createConnector();

    // Add the Connector to the Model
    RDFDataMgr.read(producerModel, IOUtils.toInputStream(connector.toString()), Lang.JSONLD);
  }

  @AfterEach
  void tearDown() {
    /*service.removeAllData();*/
  }

  @Test
  void listStatementsWithSubjectEqualTo() {
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    String result="[[https://test-broker.com/id/test/connector-subcontracted, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/BaseConnector], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.6\"], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/description, \"Description\"], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/securityProfile, https://w3id.org/idsa/code/BASE_SECURITY_PROFILE], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/publicKey, https://test-broker.com/id/test/publicKey/gtedcsd], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/hasDefaultEndpoint, https://test-broker.com/id/test/connectorEndpoint/gtedcsd], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/title, \"Title\"], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/resourceCatalog, https://test-broker.com/id/test/catalog/api/connector-subcontracted], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/outboundModelVersion, \"4.2.6\"]]";
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    FusekiUtil.joinModels(producerModel, ConnectorUtil.createModel(connectorSubcontracted));

    ModelDto modelDto=new ModelDto(URI.create("http://test"), producerModel);

    //System.out.println(fusekiConverter.modelToTurleFormat(fusekiConverter.listOfStatements2Model(modelDto.getListOfStatementsWithSubjectEqualTo(connectorSubcontracted.getId()))));
    assertEquals(modelDto.listStatementsWithSubjectEqualTo(connectorSubcontracted.getId()).toString(), result);
  }

  @Test
  void listStatementsWithObjectEqualTo_Object() {
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    String result="[[https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/], [https://test-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/], [https://test-broker.com/id/test/connector-producer, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/], [https://test-broker.com/id/test/connector-producer, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/]]";
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    FusekiUtil.joinModels(producerModel, ConnectorUtil.createModel(connectorSubcontracted));

    ModelDto modelDto=new ModelDto(URI.create("http://test"), producerModel);

    //System.out.println(fusekiConverter.modelToTurleFormat(fusekiConverter.listOfStatements2Model(modelDto.getListOfStatementsWithObjectEqualTo(URI.create("https://www.inesctec.pt/")))));
    assertEquals(modelDto.listStatementsWithObjectEqualTo(URI.create("https://www.inesctec.pt/")).toString(), result);
  }

  @Test
  void listStatementsWithObjectEqualTo_boolean() {
    modelService.listAllGraphsExceptAdmin().forEach(System.out::println);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    String result="[[https://inesctec.pt/unit-tests/03, https://w3id.org/idsa/core/graphIsActive, \"true\"^^http://www.w3.org/2001/XMLSchema#boolean], [https://inesctec.pt/unit-tests/02, https://w3id.org/idsa/core/graphIsActive, \"true\"^^http://www.w3.org/2001/XMLSchema#boolean]]";
    AdminGraphService adminGraphService=new AdminGraphService(accessor);

    // Add Connector
    adminGraphService.addConnector(URI.create("https://inesctec.pt/unit-tests/01"));
    adminGraphService.addConnector(URI.create("https://inesctec.pt/unit-tests/02"));
    adminGraphService.addConnector(URI.create("https://inesctec.pt/unit-tests/03"));

    // "Remove" the Connector
    adminGraphService.deactivateConnector(String.valueOf(URI.create("https://inesctec.pt/unit-tests/01")));

    assertEquals(adminGraphService.getGraph().listStatementsWithObjectEqualTo(true).toString(), result);
  }

  @Test
  void mapInternalIDsWithIDs() {
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Resource resource=testUtil.createResource();

    String graphName=service.addConnectorToFuseki(connector);
    service.addResourceToFuseki(resource, connector.getId());
    //service.addConnectorToFuseki(connector);

    ModelDto modelDto=jenaService.getModel(graphName);

    modelDto.getModel().listStatements().forEach(System.out::println);

    System.out.println("Printing result");
    System.err.println(modelDto.mapInternalIDsWithIDs(resource.getId()));
    List<Statement> expectedResult=new ArrayList<>();
    expectedResult.add(ResourceFactory.createStatement(
            ResourceFactory.createResource("http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692"),
            ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
            ResourceFactory.createResource("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5")
    ));
    assertEquals(modelDto.mapInternalIDsWithIDs(resource.getId()).toString(), expectedResult.toString());
  }
}