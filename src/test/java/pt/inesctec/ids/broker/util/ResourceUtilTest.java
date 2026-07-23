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
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.JenaService;
import pt.inesctec.ids.broker.service.ModelService;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.RESOURCE_CATALOG;
import static pt.inesctec.ids.broker.config.IDSConstants.core;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ResourceUtilTest {

    @Autowired
    FusekiConnectionAccessor accessor;

    TestUtil testUtil=new TestUtil();
    ModelService modelService;
    BrokerService brokerService;
    JenaService jenaService;
    FusekiConverter fusekiConverter;

    Connector connectorProducer;
    Connector connectorSubcontracted;
    Connector connectorProducerMultipleCatalogs;

    @BeforeEach
    void setUp()  {
        modelService=new ModelService(accessor);
        brokerService=new BrokerService(accessor);
        jenaService=new JenaService(accessor);

        testUtil=new TestUtil();
        fusekiConverter=new FusekiConverter();

        connectorProducer=testUtil.createConnector();
        connectorSubcontracted=testUtil.createConnectorSubcontracted();
        connectorProducerMultipleCatalogs=testUtil.createConnectorWithMultipleCatalogs();
    }

    @Test
    void addOffertToCatalog() {
        // Assert that the BD is empty
        modelService.removeAllData();
        assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

        // Prepare variables for test
        Resource resource=testUtil.createResource();
        Model resourceModel=ResourceUtil.createModel(resource);
        String graphName=brokerService.addConnectorToFuseki(connectorSubcontracted);
        ModelDto connectorModel=jenaService.getModel(graphName);

        List<Statement> catalogs=new ArrayList<>();
        connectorModel.getModel().listStatements(null, null, ResourceFactory.createProperty(RESOURCE_CATALOG))
                .forEachRemaining(catalogs::add);

        // Before test, assert that the connector model does not have a statement that connects the catalog and the offer
        assertThat(connectorModel.getModel().listStatements(catalogs.get(0).getSubject(), ResourceFactory.createProperty(core+"offeredResource"), (RDFNode) null).toList().size())
                .isEqualTo(0);

        // Prepare environment to test
        ResourceUtil.addSameAsToResource(resource, resourceModel);
        ResourceUtil.rewriteSubjects(resource, connectorModel, resourceModel, FusekiUtil.buildNewSubjectName(connectorModel.getName().toString(), URI.create(catalogs.stream().findFirst().get().getObject().toString())));
        FusekiUtil.joinModels(connectorModel.getModel(), resourceModel);

        // Test starts here
        ResourceUtil.addOffertToCatalog(connectorModel, resource.getId());
        // After test, assert that the connector model HAS a statement that connects the catalog and the offer
        assertThat(connectorModel.getModel().listStatements(catalogs.get(0).getSubject(), ResourceFactory.createProperty(core+"offeredResource"), (RDFNode) null).toList().size())
                .isEqualTo(1);

    }

    @Test
    void addSameAsToResource() {
        // Prepare variables for test
        String graphName=FusekiUtil.buildGraphName(accessor, GraphNameUtil.getUniqueIdentifier(connectorSubcontracted.getId()));
        ModelDto connectorModel=new ModelDto(graphName, ConnectorUtil.createModel(connectorSubcontracted));
        Resource resource=testUtil.createResource_WithPublisherAndSovereign();
        Model resourceModel=ResourceUtil.createModel(resource);
        FusekiUtil.joinModels(connectorModel.getModel(), resourceModel);

        // Before test, assert that the model does not have a statement that contains the Resource SameAs
        assertThat(connectorModel.getModel().listStatements()
                .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
                .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(resource.getId().toString()))).toList().size())
                .isEqualTo(0);

        // Test starts here
        ResourceUtil.addSameAsToResource(resource, connectorModel.getModel());

        // After test, assert that the model HAS a statement that contains the Resource SameAs
        assertThat(connectorModel.getModel().listStatements()
                .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
                .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(resource.getId().toString()))).toList().size())
                .isEqualTo(1);

    }

    @Test
    void getResourceSameAsStatementFromDB() {
        // Assert that the BD is empty
        modelService.removeAllData();
        assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

        String expectedOutput="[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5]";

        Resource resource=testUtil.createResource();
        String graphName=brokerService.addConnectorToFuseki(connectorSubcontracted);
        ModelDto connectorModel=jenaService.getModel(graphName);

        // Test starts here
        Optional<Statement> sameAsStatement= ResourceUtil.getResourceSameAsStatementFromDB(connectorModel, resource);
        // Should be empty, since I did not add the ResourceModel to the Connector Model
        assertThat(sameAsStatement).isEmpty();


        // Add Resource Model
        brokerService.addResourceToFuseki(resource, connectorSubcontracted.getId());

        // Retrieve Updated connectorModel from DB
        connectorModel=jenaService.getModel(graphName);
        sameAsStatement= ResourceUtil.getResourceSameAsStatementFromDB(connectorModel, resource);
        System.out.println(sameAsStatement);

        assertThat(sameAsStatement).isPresent();
        assertThat(sameAsStatement.get().toString()).isEqualTo(expectedOutput);

    }

    @Test
    void getArtifactSameAsStatementFromDB() {
        // Assert that the BD is empty
        modelService.removeAllData();
        assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

        URI artifactID=URI.create("https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a");
        String expectedOutput="[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692/1564849632/939358726, http://www.w3.org/2002/07/owl#sameAs, "+artifactID+"]";

        Resource resource=testUtil.createResourceWithMultipleArtefacts();
        String graphName=brokerService.addConnectorToFuseki(connectorSubcontracted);
        ModelDto connectorModel=jenaService.getModel(graphName);

        // Test starts here
        Optional<Statement> sameAsStatement= ResourceUtil.getArtifactSameAsStatementFromDB(connectorModel, artifactID);
        // Should be empty, since I did not add the ResourceModel to the Connector Model
        assertThat(sameAsStatement).isEmpty();


        // Add Resource Model
        brokerService.addResourceToFuseki(resource, connectorSubcontracted.getId());

        // Retrieve Updated connectorModel from DB
        connectorModel=jenaService.getModel(graphName);
        sameAsStatement= ResourceUtil.getArtifactSameAsStatementFromDB(connectorModel, artifactID);
        System.out.println(sameAsStatement);

        assertThat(sameAsStatement).isPresent();
        assertThat(sameAsStatement.get().toString()).isEqualTo(expectedOutput);

    }

    @Test
    void rewriteSubjects() {
        String expectedOutput="[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807/-1474186913, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Permission]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807/-1474186913, https://w3id.org/idsa/core/action, https://w3id.org/idsa/code/USE]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807/-1474186913, https://w3id.org/idsa/core/description, \"PROVIDE_ACCESS\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807/-1474186913, https://w3id.org/idsa/core/title, \"PROVIDE_ACCESS\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632/939358726, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Artifact]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632/939358726, https://w3id.org/idsa/core/byteSize, \"0\"^^http://www.w3.org/2001/XMLSchema#integer]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632/939358726, https://w3id.org/idsa/core/checkSum, \"0\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632/939358726, https://w3id.org/idsa/core/creationDate, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632/939358726, https://w3id.org/idsa/core/fileName, \"test-file\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807, https://w3id.org/idsa/core/permission, http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807/-1474186913]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807, https://w3id.org/idsa/core/contractDate, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807, https://w3id.org/idsa/core/contractEnd, \"2025-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807, https://w3id.org/idsa/core/contractStart, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ContractOffer]\n" +
                "[https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a, https://w3id.org/idsa/core/accessURL, http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692]\n" +
                "[https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a, https://w3id.org/idsa/core/endpointDocumentation, https://url.for.documentation]\n" +
                "[https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ConnectorEndpoint]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632, https://w3id.org/idsa/core/instance, http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632/939358726]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632, https://w3id.org/idsa/core/created, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Representation]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632, https://w3id.org/idsa/core/language, https://w3id.org/idsa/code/EN]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632, https://w3id.org/idsa/core/mediaType, https://w3id.org/idsa/autogen/iANAMediaType/3b0dbc76-982f-4643-b848-8292545bffee]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632, https://w3id.org/idsa/core/modified, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/resourceEndpoint, https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/representation, http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1564849632]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/modified, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/description, \"Information about product P20230303\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/version, \"1\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/Resource]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/contractOffer, http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692/1053123807]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/keyword, \"term1\"]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/created, \"2022-03-23T12:23:22.000Z\"^^http://www.w3.org/2001/XMLSchema#dateTimeStamp]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/language, https://w3id.org/idsa/code/EN]\n" +
                "[http://test-hostname:8082/connectors/t-broker.com/id/test/connector-subcontracted/-435574692, https://w3id.org/idsa/core/title, \"PDP-P20230303\"]\n" +
                "[https://w3id.org/idsa/autogen/iANAMediaType/3b0dbc76-982f-4643-b848-8292545bffee, https://w3id.org/idsa/core/filenameExtension, \"application/octet-stream\"]\n" +
                "[https://w3id.org/idsa/autogen/iANAMediaType/3b0dbc76-982f-4643-b848-8292545bffee, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/IANAMediaType]\n";
        // Creates a list using the \n as the separator for the rows
        ArrayList<String> expectedOutputAsList = new ArrayList<>(Arrays.asList(expectedOutput.split("\n")));

        Resource resource=testUtil.createResource();
        Model resourceAsModel=ResourceUtil.createModel(resource);

        ModelDto connectorModel=new ModelDto(FusekiUtil.buildGraphName(accessor, GraphNameUtil.getUniqueIdentifier(connectorSubcontracted.getId())), ConnectorUtil.createModel(connectorSubcontracted));

        // Test starts here
        Model rewrittenModel= ResourceUtil.rewriteSubjects(resource, connectorModel, resourceAsModel, FusekiUtil.buildNewSubjectName(connectorModel.getName().toString(), URI.create("http://resource/catalog")));

        System.out.println(fusekiConverter.modelToTurleFormat(rewrittenModel));
        fusekiConverter.model2ListOfStatements(rewrittenModel).forEach(System.err::println);

        assertEquals(rewrittenModel.listStatements().toList().size(), expectedOutputAsList.size());

    }
}