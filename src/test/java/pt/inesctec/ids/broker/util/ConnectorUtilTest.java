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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.*;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ConnectorUtilTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorUtilTest.class);

    @Autowired
    FusekiConnectionAccessor accessor;

    TestUtil testUtil;

    ModelService service;
    BrokerService brokerService;

    FusekiConverter fusekiConverter;

    Connector connectorSubcontracted;

    @BeforeEach
    void setUp()  {
        service=new ModelService(accessor);
        brokerService=new BrokerService(accessor);

        testUtil=new TestUtil();
        fusekiConverter=new FusekiConverter();

        connectorSubcontracted=testUtil.createConnectorSubcontracted();
    }

    @Test
    void addSameAsToConnector() {
        // Prepare variables for test
        String graphName=FusekiUtil.buildGraphName(accessor, GraphNameUtil.getUniqueIdentifier(connectorSubcontracted.getId()));
        ModelDto connectorModel=new ModelDto(graphName, ConnectorUtil.createModel(connectorSubcontracted));


        // Before test, assert that the connector model does not have a statement that contains the SameAs
        assertThat(connectorModel.getModel().listStatements()
                .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
                .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(connectorSubcontracted.getId().toString()))).toList().size())
                .isEqualTo(0);

        // Test starts here
        ConnectorUtil.addSameAsToConnector(connectorSubcontracted, connectorModel.getModel());

        // After test, assert that the connector model HAS a statement that that contains the SameAs
        assertThat(connectorModel.getModel().listStatements()
                        .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
                        .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(connectorSubcontracted.getId().toString()))).toList().size())
                .isEqualTo(1);

    }

    @Test
    void rewriteSubjects() {
        String expectedOutput="[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/436781894, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ResourceCatalog]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1596389541, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ConnectorEndpoint]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1596389541, https://w3id.org/idsa/core/accessURL, https://host.docker.internal:8080/test/subcontracted/api/ids/data]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1175066225, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/PublicKey]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1175066225, https://w3id.org/idsa/core/keyType, https://w3id.org/idsa/code/RSA]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1175066225, https://w3id.org/idsa/core/keyValue, \"S2V5VmFsdWVFeGFtcGxl\"]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/outboundModelVersion, \"4.2.6\"]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/description, \"Description\"]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/resourceCatalog, http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/436781894]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/BaseConnector]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/publicKey, http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1175066225]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.6\"]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/securityProfile, https://w3id.org/idsa/code/BASE_SECURITY_PROFILE]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/title, \"Title\"]\n" +
                "[http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted, https://w3id.org/idsa/core/hasDefaultEndpoint, http://test-hostname:3032/connectors/t-broker.com/id/test/connector-subcontracted/-1596389541]\n";
        // Creates a list using the \n as the separator for the rows
        ArrayList<String> expectedOutputAsList = new ArrayList<>(Arrays.asList(expectedOutput.split("\n")));
        //Model expectedOutputModel= fusekiConverter.convertStringsToRDFModel(expectedOutputAsList);

        Model connectorSubcontractdAsModel=ConnectorUtil.createModel(connectorSubcontracted);

        // Test starts here
        Model rewrittenModel= ConnectorUtil.rewriteSubjects(connectorSubcontracted, connectorSubcontractdAsModel, FusekiUtil.buildGraphName(accessor, GraphNameUtil.getUniqueIdentifier(connectorSubcontracted.getId())));

        System.out.println(fusekiConverter.modelToTurleFormat(rewrittenModel));
        fusekiConverter.model2ListOfStatements(rewrittenModel).forEach(System.err::println);

        LOGGER.info("Assert That the rewrittenModel is the same size as the expectedOutputAsList");
        assertEquals(rewrittenModel.listStatements().toList().size(), expectedOutputAsList.size());

    }

    @Test
    void getConnectorModelFromDB() {
        String expectedModelToRemove="[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ConnectorEndpoint]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/connectorEndpoint/gtedcsd]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950, https://w3id.org/idsa/core/accessURL, https://host.docker.internal:8080/test/subcontracted/api/ids/data]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/resourceCatalog, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/description, \"Description\"]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/connector-subcontracted]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/BaseConnector]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.6\"]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/securityProfile, https://w3id.org/idsa/code/BASE_SECURITY_PROFILE]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/hasDefaultEndpoint, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/outboundModelVersion, \"4.2.6\"]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/publicKey, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/title, \"Title\"]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/PublicKey]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/publicKey/gtedcsd]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, https://w3id.org/idsa/core/keyType, https://w3id.org/idsa/code/RSA]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, https://w3id.org/idsa/core/keyValue, \"S2V5VmFsdWVFeGFtcGxl\"]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ResourceCatalog]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/catalog/api/connector-subcontracted]\n" +
                "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561, https://w3id.org/idsa/core/offeredResource, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692]";
        // Creates a list using the \n as the separator for the rows
        ArrayList<String> expectedModelToRemoveAsList = new ArrayList<>(Arrays.asList(expectedModelToRemove.split("\n")));

        Model expectedModel= fusekiConverter.convertStringsToRDFModel(expectedModelToRemoveAsList);


        // Assert that the BD is empty
        service.removeAllData();
        assertEquals(service.listAllGraphsExceptAdmin().size(), 0);

        Resource resource=testUtil.createResourceWithMultipleArtefacts();

        // Add Connector To Fuseki
        brokerService.addConnectorToFuseki(connectorSubcontracted);

        // Add Resource to Fuseki
        brokerService.addResourceToFuseki(resource, connectorSubcontracted.getId());

        ModelDto connectorModel=service.getModelWithConnectorIDEqualTo(connectorSubcontracted.getId()).get();

        LOGGER.info("Get Connector model");
        Model modelToRemove= ConnectorUtil.getConnectorModelFromDB(connectorModel.getName(), connectorModel);
        System.out.println(fusekiConverter.modelToTurleFormat(modelToRemove));
        fusekiConverter.model2ListOfStatements(modelToRemove).forEach(System.err::println);

        LOGGER.info("Assert That the modelToRemove is the same size as the expectedModelToRemoveAsList");
        assertEquals(modelToRemove.listStatements().toList().size(), expectedModelToRemoveAsList.size());

        List<Statement> listOfTypes=modelToRemove.listStatements()
                .filterKeep(triple -> triple.getObject().toString().equals(RESOURCE_CATALOG)
                        || triple.getObject().toString().equals((CONNECTOR))
                        || triple.getObject().toString().equals((CONNECTOR_ENDPOINT))
                        || triple.getObject().toString().equals((PUBLIC_KEY)))
                        .toList();

        assertThat(listOfTypes.size()).isEqualTo(4);
        assertThat(modelToRemove.size()).isEqualTo(22);
        System.out.println(FusekiUtil.getStatementsDiff(modelToRemove, expectedModel));

        assertFalse(FusekiUtil.getStatementsDiff(modelToRemove, expectedModel).hasDiff());
    }

    @Test
    void createModel() {
        Model resultModel=ConnectorUtil.createModel(connectorSubcontracted);
        resultModel.write(System.out, "TURTLE");

        assertThat(CompareUtil.compareStrings(
            fusekiConverter.modelToTurleFormat(resultModel).toString(),
            "@prefix ids:  <https://w3id.org/idsa/core/> .\n" +
                "@prefix idsc: <https://w3id.org/idsa/code/> .\n" +
                "\n" +
                "<https://test-broker.com/id/test/publicKey/gtedcsd>\n" +
                "        a             ids:PublicKey ;\n" +
                "        ids:keyType   idsc:RSA ;\n" +
                "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
                "\n" +
                "<https://test-broker.com/id/test/connectorEndpoint/gtedcsd>\n" +
                "        a              ids:ConnectorEndpoint ;\n" +
                "        ids:accessURL  <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n" +
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
                "        ids:title                 \"Title\" .")).isTrue();

    }
}