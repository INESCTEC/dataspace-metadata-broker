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

import de.fraunhofer.iais.eis.*;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.FusekiConstants.SEPARATOR;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class FusekiUtilTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(FusekiUtilTest.class);

  private ModelService modelService;
  BrokerService service;
  FusekiConverter converter=new FusekiConverter();
  TestUtil testUtil=new TestUtil();

  @Autowired
  FusekiConnectionAccessor accessor;

  Model model = ModelFactory.createDefaultModel();
  String uuid=null;
  String connectorUniqueID =null;

  @BeforeEach
  void setUp() throws IOException {
    service=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    File file = new ClassPathResource("conf/configmodel.json").getFile();

    testUtil.removeAllGraphsFromDB(accessor);

    // Read JSON file and convert it into a Jena Model
    FileInputStream inputStream = new FileInputStream(file);
    Connector connector=ConnectorUtil.deserialize(inputStream);

    connectorUniqueID = GraphNameUtil.getConnectorUniqueIDFromConnectorURL(connector.getId());

    // Parse JSON-DL and convert it into a Jena Model
    RDFDataMgr.read(model, IOUtils.toInputStream(connector.toString()), Lang.JSONLD);

    // Replace Subjects
    uuid=modelService.replaceSubject(model, "connector", connectorUniqueID);

    //ConnectorUtil.createConnectorModel(model, connector);
  }

  @AfterEach
  void tearDown() {
    /*service.removeAllData();*/
  }

  @Test
  void joinModels_ModelsAreDifferent() {
    // Created the model Used to compare
    String result="@prefix ids:  <https://w3id.org/idsa/core/> .\n" +
            "@prefix idsc: <https://w3id.org/idsa/code/> .\n" +
            "\n" +
            "<https://test-broker.com/id/test/publicKey/gtedcsd>\n" +
            "        a             ids:PublicKey ;\n" +
            "        ids:keyType   idsc:RSA ;\n" +
            "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connector-subcontracted>\n" +
            "        a                         ids:BaseConnector ;\n" +
            "        ids:curator               <https://www.inesctec.pt/> ;\n" +
            "        ids:description           \"Description\" ;\n" +
            "        ids:hasDefaultEndpoint    <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> ;\n" +
            "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
            "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
            "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
            "        ids:publicKey             <https://test-broker.com/id/test/publicKey/gtedcsd> ;\n" +
            "        ids:securityProfile       idsc:BASE_SECURITY_PROFILE ;\n" +
            "        ids:title                 \"Title\" .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connectorEndpoint/gtedcsd>\n" +
            "        a              ids:ConnectorEndpoint ;\n" +
            "        ids:accessURL  <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connector-producer>\n" +
            "        a                         ids:BaseConnector ;\n" +
            "        ids:curator               <https://www.inesctec.pt/> ;\n" +
            "        ids:description           \"Description\" ;\n" +
            "        ids:hasDefaultEndpoint    <https://test-broker.com/id/test/connectorEndpoint/dhiasdcna> ;\n" +
            "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
            "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
            "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
            "        ids:publicKey             <https://test-broker.com/id/test/publicKey/dhiasdcna> ;\n" +
            "        ids:securityProfile       idsc:BASE_SECURITY_PROFILE ;\n" +
            "        ids:title                 \"Title\" .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connectorEndpoint/dhiasdcna>\n" +
            "        a              ids:ConnectorEndpoint ;\n" +
            "        ids:accessURL  <https://host.docker.internal:8080/test/connector/api/ids/data> .\n" +
            "\n" +
            "<https://test-broker.com/id/test/publicKey/dhiasdcna>\n" +
            "        a             ids:PublicKey ;\n" +
            "        ids:keyType   idsc:RSA ;\n" +
            "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .";

    Model resultModel=ModelFactory.createDefaultModel();
    RDFDataMgr.read(resultModel, IOUtils.toInputStream(result), Lang.TURTLE);


    // Create a new Model
    Connector conn=testUtil.createConnector();
    connectorUniqueID = GraphNameUtil.getConnectorUniqueIDFromConnectorURL(conn.getId());
    // Parse JSON-DL and convert it into a Jena Model
    Model newModel=ModelFactory.createDefaultModel();
    RDFDataMgr.read(newModel, IOUtils.toInputStream(conn.toString()), Lang.JSONLD);


    // Join the model created on StartUp with the one created on this test
    FusekiUtil.joinModels(model, newModel);

    System.out.println(converter.modelToTurleFormat(model));

    // Check if the models are the same. They are not!!
    assertNotEquals(FusekiUtil.getStatementsDiff(model, resultModel).hasDiff(), false);
  }

  @Test
  void joinModels_ModelsAreTheSame() {
    // Creates the model used to compare
    String result="@prefix ids:  <https://w3id.org/idsa/core/> .\n" +
            "@prefix idsc: <https://w3id.org/idsa/code/> .\n" +
            "\n" +
            "<https://test-broker.com/id/test/publicKey/gtedcsd>\n" +
            "        a             ids:PublicKey ;\n" +
            "        ids:keyType   idsc:RSA ;\n" +
            "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
            "\n" +
            "<https://test-broker.com/id/test/catalog/api/connector-subcontracted>\n" +
            "        a       ids:ResourceCatalog .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connector-subcontracted>\n" +
            "        a                         ids:BaseConnector ;\n" +
            "        ids:curator               <https://www.inesctec.pt/> ;\n" +
            "        ids:description           \"Description\" ;\n" +
            "        ids:hasDefaultEndpoint    <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> ;\n" +
            "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
            "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
            "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
            "        ids:publicKey             <https://test-broker.com/id/test/publicKey/gtedcsd> ;\n" +
            "        ids:resourceCatalog       <https://test-broker.com/id/test/catalog/api/connector-subcontracted> ;\n" +
            "        ids:securityProfile       idsc:BASE_SECURITY_PROFILE ;\n" +
            "        ids:title                 \"Title\" .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connectorEndpoint/gtedcsd>\n" +
            "        a              ids:ConnectorEndpoint ;\n" +
            "        ids:accessURL  <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n" +
            "\n" +
            "<https://test-broker.com/id/test/catalog/api/connector-producer>\n" +
            "        a       ids:ResourceCatalog .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connector-producer>\n" +
            "        a                         ids:BaseConnector ;\n" +
            "        ids:curator               <https://www.inesctec.pt/> ;\n" +
            "        ids:description           \"Description\" ;\n" +
            "        ids:hasDefaultEndpoint    <https://test-broker.com/id/test/connectorEndpoint/dhiasdcna> ;\n" +
            "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
            "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
            "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
            "        ids:publicKey             <https://test-broker.com/id/test/publicKey/dhiasdcna> ;\n" +
            "        ids:resourceCatalog       <https://test-broker.com/id/test/catalog/api/connector-producer> ;\n" +
            "        ids:securityProfile       idsc:BASE_SECURITY_PROFILE ;\n" +
            "        ids:title                 \"Title\" .\n" +
            "\n" +
            "<https://test-broker.com/id/test/connectorEndpoint/dhiasdcna>\n" +
            "        a              ids:ConnectorEndpoint ;\n" +
            "        ids:accessURL  <https://host.docker.internal:8080/test/connector/api/ids/data> .\n" +
            "\n" +
            "<https://test-broker.com/id/test/publicKey/dhiasdcna>\n" +
            "        a             ids:PublicKey ;\n" +
            "        ids:keyType   idsc:RSA ;\n" +
            "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .";

    Model resultModel=ModelFactory.createDefaultModel();
    RDFDataMgr.read(resultModel, IOUtils.toInputStream(result), Lang.TURTLE);



    // Creates a new Model - Producer
    Connector conn=testUtil.createConnector();
    connectorUniqueID = GraphNameUtil.getConnectorUniqueIDFromConnectorURL(conn.getId());
    // Parse JSON-DL and convert it into a Jena Model
    Model newModel=ModelFactory.createDefaultModel();
    RDFDataMgr.read(newModel, IOUtils.toInputStream(conn.toString()), Lang.JSONLD);

    // Create a new Model - Subcontracted
    Connector conn2=testUtil.createConnectorSubcontracted();
    connectorUniqueID = GraphNameUtil.getConnectorUniqueIDFromConnectorURL(conn2.getId());
    // Parse JSON-DL and convert it into a Jena Model
    Model newModel2=ModelFactory.createDefaultModel();
    RDFDataMgr.read(newModel2, IOUtils.toInputStream(conn2.toString()), Lang.JSONLD);

    // Join the two models (Producer and Subcontacted)
    FusekiUtil.joinModels(newModel, newModel2);


    System.out.println(converter.modelToTurleFormat(newModel));

    System.out.println(FusekiUtil.getStatementsDiff(newModel, resultModel));
    // Check if the models are the same (ResultModel and newModel[which is the joined model])
    assertEquals(FusekiUtil.getStatementsDiff(newModel, resultModel).hasDiff(), false);
  }

}