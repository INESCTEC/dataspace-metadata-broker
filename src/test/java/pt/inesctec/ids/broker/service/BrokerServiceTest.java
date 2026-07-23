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
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.builder.TriplesBuilder;
import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.converter.XMLGregorianCalendarConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.dto.Triples;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;
import pt.inesctec.ids.broker.exceptions.ResourceNotFoundException;
import pt.inesctec.ids.broker.util.*;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static pt.inesctec.ids.broker.config.FusekiConstants.SEPARATOR;
import static pt.inesctec.ids.broker.config.IDSConstants.*;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class BrokerServiceTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerServiceTest.class);
  @Autowired
  FusekiConnectionAccessor accessor;

  TestUtil testUtil;

  BrokerService service;
  ModelService modelService;
  AdminGraphService adminGraphService;
  JenaService jenaService;

  Connector connector;

  @BeforeEach
  void setUp() throws IOException {
    service=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    adminGraphService=new AdminGraphService(accessor);
    jenaService=new JenaService(accessor);
    testUtil=new TestUtil();

    testUtil.removeAllGraphsFromDB(accessor);
    //service.uploadData();

    File file = new ClassPathResource("docs/connectorSelf-description.json").getFile();
    FileInputStream inputStream = new FileInputStream(file);
    Serializer serializer=new Serializer();

    connector=serializer.deserialize(IOUtils.toString(inputStream), Connector.class);
  }


  @Test
  void addConnectorToFuseki() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;

    //Check if the Connector was added to the Admin Graph
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName)).size(), 1);

    // Check if the Connector was added to the DB
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    assertTrue(connectorModel.isPresent());
    assertEquals(connectorModel.get().getModel().size(), 21);

    assertFalse(jenaService.getModel(graphName).getModel().isEmpty());
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

  }

  /**
   * Tries to register a Connector that is already registered.
   * However, the Connector is disabled ( It is a true ConnectorUpdateMessage)
   * */
  @Test
  void addConnectorToFuseki_ConnectorIsAlreadyRegistered_ItIsDisable() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    // Get AdminModel and assert that it contains the new connector model with the connector disabled
    ModelDto adminModelWithDisabledConn=adminGraphService.getGraph();
    // The -1 removes the admin statement
    Integer numberOfConnectorRegisteredOnAdminGraph=adminModelWithDisabledConn.getModel().listStatements().toList().size()-1;
    Triples graph = new TriplesBuilder()
            .subject(graphName)
            .predicate(GRAPH_IS_ACTIVE)
            .object(false)
            .build();
    assertTrue(adminModelWithDisabledConn.getModel().contains(graph.toGraphIsActiveStatement()));

    // Add Connector
    String graphName2=service.addConnectorToFuseki(conn);

    // Asserts that the graphName are the same
    assertEquals(graphName2, graphName2);

    // Get the updated AdminModel and assert that it contains the new connector model (with the connector enabled)
    adminModelWithDisabledConn=adminGraphService.getGraph();
    graph.setObject(true);
    assertTrue(adminModelWithDisabledConn.getModel().contains(graph.toGraphIsActiveStatement()));
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);


  }

  /**
   * Tries to register a Connector that is already registered.
   * However, the Connector is disabled and has a new Catalog( It is a true ConnectorUpdateMessage)
   * */
  @Test
  void addConnectorToFuseki_ConnectorIsAlreadyRegistered_ItIsDisableAndHasUpdates() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    FusekiConverter fusekiConverter=new FusekiConverter();

    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);
    Model originalModel=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel();
    int numberOfRowsOnConnectorModel=originalModel.listStatements().toList().size();

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    // Get AdminModel and assert that it contains the new connector model with the connector disabled
    ModelDto adminModelWithDisabledConn=adminGraphService.getGraph();
    Triples graph = new TriplesBuilder()
            .subject(graphName)
            .predicate(GRAPH_IS_ACTIVE)
            .object(false)
            .build();
    // The -1 removes the admin statement
    Integer numberOfConnectorRegisteredOnAdminGraph=adminModelWithDisabledConn.getModel().listStatements().toList().size()-1;
    assertTrue(adminModelWithDisabledConn.getModel().contains(graph.toGraphIsActiveStatement()));
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Add a Catalog to the Connector -> To make the Connector different
    List<ResourceCatalog> resourceCatalogs=conn.getResourceCatalog();
    resourceCatalogs.add(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer")).build());
    conn.setResourceCatalog(resourceCatalogs);

    /*System.out.println("Before updating Connector");
    originalModel.listStatements().forEach(System.out::println);
    System.out.println(fusekiConverter.modelToTurleFormat(modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel()));*/

    // Add Connector
    String graphName2=service.addConnectorToFuseki(conn);

    // Check if the Connector was added to the DB
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    int originalSize=connectorModel.get().getModel().listStatements().toList().size();
    assertTrue(connectorModel.isPresent());
    assertFalse(jenaService.getModel(graphName).getModel().isEmpty());

    /*System.out.println("After updating Connector");
    connectorModel.get().getModel().listStatements().forEach(System.out::println);
    System.out.println(fusekiConverter.modelToTurleFormat(connectorModel.get().getModel()));*/

    // Asserts that the graphName are the same
    assertEquals(graphName2, graphName2);

    // Get the updated AdminModel and assert that it contains the new connector model (with the connector enabled)
    adminModelWithDisabledConn=adminGraphService.getGraph();
    // The -1 removes the admin statement
    numberOfConnectorRegisteredOnAdminGraph=adminModelWithDisabledConn.getModel().listStatements().toList().size()-1;
    graph.setObject(true);
    assertTrue(adminModelWithDisabledConn.getModel().contains(graph.toGraphIsActiveStatement()));
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Assert that after adding another Catalog, the Connector Model size increased by 3 (1 line for type, 1 for SameAs and 1 for the Resource on the connector)
    assertEquals(numberOfRowsOnConnectorModel+3, originalSize);



    /* Test replacing the catalogue (decrease one Catalogue)*/

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    // Add a Catalog to the Connector -> Remove One Catalog
    conn.setResourceCatalog(List.of(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer")).build()));

    // Add Connector
    graphName2=service.addConnectorToFuseki(conn);

    // Asserts that the graphName are the same
    assertEquals(graphName2, graphName2);

    // Get the updated AdminModel and assert that it contains the new connector model (with the connector enabled)
    adminModelWithDisabledConn=adminGraphService.getGraph();
    // The -1 removes the admin statement
    numberOfConnectorRegisteredOnAdminGraph=adminModelWithDisabledConn.getModel().listStatements().toList().size()-1;
    graph.setObject(true);
    assertTrue(adminModelWithDisabledConn.getModel().contains(graph.toGraphIsActiveStatement()));
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Update the ConnectorModel
    connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());

    System.out.println("originalSize: "+originalSize);
    System.out.println("new Model: "+connectorModel.get().getModel().listStatements().toList().size());

    // Check if the model size is the same as the original (Original size (numberOfRowsOnConnectorModel) also only had one Catalogue)
    assertEquals(connectorModel.get().getModel().listStatements().toList().size(), numberOfRowsOnConnectorModel);

  }

  /**
   * Tries to register a Connector that is already registered.
   * However, the Connector has a new Catalog ( It is a true ConnectorUpdateMessage)
   * */
  @Test
  void addConnectorToFuseki_ConnectorIsAlreadyRegistered_ThereAreNewChanges() {
    testUtil.removeAllGraphsFromDB(accessor);
    // Get AdminModel
    ModelDto adminModel=adminGraphService.getGraph();
    // The -1 removes the admin statement
    Integer numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;

    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 0);


    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);
    // Add Resource
    service.addResourceToFuseki(testUtil.createResource(), conn.getId());

    // Check if the Connector was added to the DB
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    int originalSize=connectorModel.get().getModel().listStatements().toList().size();

    // Verify admin Graph
    adminModel=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;

    assertTrue(connectorModel.isPresent());
    assertFalse(jenaService.getModel(graphName).getModel().isEmpty());
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);


    // Add a Catalog to the Connector -> To make the Connector different
    List<ResourceCatalog> resourceCatalogs=conn.getResourceCatalog();
    resourceCatalogs.add(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer")).build());
    conn.setResourceCatalog(resourceCatalogs);

    // Add the Updated Connector
    service.addConnectorToFuseki(conn);
    connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    // Verify admin Graph
    adminModel=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;


    // Check if the model size was only incremented by 3
    System.out.println("originalSize: "+originalSize);
    System.out.println("new Model: "+connectorModel.get().getModel().listStatements().toList().size());
    assertEquals(connectorModel.get().getModel().listStatements().toList().size(), originalSize+3);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  /**
   * Tries to register a Connector that is already registered.
   * However, the Connector replaces the Catalog that has the offer with another( It is a true ConnectorUpdateMessage)
   * */
  @Test
  void addConnectorToFuseki_ConnectorIsAlreadyRegistered_ThereAreNewChanges_CatalogWithOfferIsRemoved() {
    testUtil.removeAllGraphsFromDB(accessor);
    // Get AdminModel
    ModelDto adminModel=adminGraphService.getGraph();
    // The -1 removes the admin statement
    Integer numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;

    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 0);

    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);
    // Add Resource
    service.addResourceToFuseki(testUtil.createResource(), conn.getId());

    // Check if the Connector was added to the DB
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    int originalSize=connectorModel.get().getModel().listStatements().toList().size();

    System.out.println("Original Size: "+originalSize);
    System.out.println("Print resources after adding it:");
    connectorModel.get().getModel().listStatements(null, null, ResourceFactory.createResource(RESOURCE)).forEach(System.out::println);
    assertTrue(connectorModel.isPresent());
    assertFalse(jenaService.getModel(graphName).getModel().isEmpty());

    // Get AdminModel
    adminModel=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Add a Catalog to the Connector -> Remove Catalog that had the Offer
    conn.setResourceCatalog(List.of(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer")).build()));

    // Add the Updated Connector ( The offer changes from the original Catalog to the new one)
    service.addConnectorToFuseki(conn);
    connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());

    System.out.println("Print updated resources:");
    connectorModel.get().getModel().listStatements(null, null, ResourceFactory.createResource(RESOURCE)).forEach(System.out::println);
    // Check if the model size adds 3
    assertEquals(connectorModel.get().getModel().listStatements().toList().size(), originalSize);
    // Get AdminModel
    adminModel=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  /**
   * Tries to register a Connector that is already registered.
   * There are no changes since the last time the Connector was registered
   * */
  @Test
  void addConnectorToFuseki_ConnectorIsAlreadyRegistered_NoChangesWereMade() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);


    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;

    //Check if the Connector was added to the Admin Graph
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName)).size(), 1);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);


    // Check if the Connector was added to the DB
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    int originalSize=connectorModel.get().getModel().listStatements().toList().size();
    assertTrue(connectorModel.isPresent());

    assertFalse(jenaService.getModel(graphName).getModel().isEmpty());

    // Add Connector Again
    service.addConnectorToFuseki(conn);
    connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());

    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Check if the model size is the same
    assertEquals(connectorModel.get().getModel().listStatements().toList().size(), originalSize);
  }


  /**
   * Tries to register a Connector that is already registered.
   * However, the Connector has a new Catalog ( It is a true ConnectorUpdateMessage)
   * */
  @Test
  void addConnectorToFuseki_IllegalArgument() {
    testUtil.removeAllGraphsFromDB(accessor);
    // Get AdminModel
    ModelDto adminModel=adminGraphService.getGraph();
    // The -1 removes the admin statement
    Integer numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;

    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 0);


    Connector conn= new BaseConnectorBuilder(URI.create("https://stvgodigital.pt/ids/connector/id/producer-01"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/connectorEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/test/connector/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            ._curator_(URI.create("https://www.inesctec.pt/"))
            ._description_(new TypedLiteral("Description"))
            ._title_(new TypedLiteral("Title"))
            ._publicKey_(new PublicKeyBuilder(URI.create("https://test-broker.com/id/test/publicKey/dhiasdcna"))
                    ._keyValue_("KeyValueExample".getBytes())
                    ._keyType_(KeyType.RSA)
                    .build())
            ._resourceCatalog_(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/catalog/api/connector-producer"))
                    .build())
            .build();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);
    // Add Resource
    service.addResourceToFuseki(testUtil.createResource(), conn.getId());

    // Check if the Connector was added to the DB
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    int originalSize=connectorModel.get().getModel().listStatements().toList().size();

    // Verify admin Graph
    adminModel=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;

    assertTrue(connectorModel.isPresent());
    assertFalse(jenaService.getModel(graphName).getModel().isEmpty());
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);


    // Add a Catalog to the Connector -> To make the Connector different
    List<ResourceCatalog> resourceCatalogs=conn.getResourceCatalog();
    resourceCatalogs.add(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer")).build());
    conn.setResourceCatalog(resourceCatalogs);

    // Add the Updated Connector
    service.addConnectorToFuseki(conn);
    connectorModel=modelService.getModelWithConnectorIDEqualTo(conn.getId());
    // Verify admin Graph
    adminModel=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminModel.getModel().listStatements().toList().size()-1;


    // Check if the model size was only incremented by 3
    assertEquals(connectorModel.get().getModel().listStatements().toList().size(), originalSize+3);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  @Test
  void addResourceToFuseki() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);
    //service.addConnectorToFuseki(connector);

    // Add Resource
    String response =service.addResourceToFuseki(testUtil.createResource(), URI.create("https://test-broker.com/id/test/connector-producer"));

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;


    // Checks that the response is the same as the graphName stored on the DB
    System.out.println(response);
    System.out.println(jenaService.getModel(response).getName());

    assertNotNull(jenaService.getModel(response));
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  @Test
  void addResourceToFuseki_ConnectorIsNotActive() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    // Add Resource. Throws an error since the connector is disabled
    Exception exception= assertThrows(ResourceNotFoundException.class, () ->
            service.addResourceToFuseki(testUtil.createResource(), conn.getId()));
    assertEquals(exception.getMessage(), "Connector not found with ID '"+ conn.getId()+"'. Did you send a ConnectorUpdateMessage yet?");
  }

  @Test
  void addResourceToFuseki_WithoutCatalog() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnectorWithoutResourceCatalog();

    // Add Connector
    service.addConnectorToFuseki(conn);

    String errorMessage="Catalog of connector "+conn.getId()+" not found. Did you send a ConnectorUpdateMessage yet?";
    // Add Resource
    Exception exception = assertThrows(InvalidValueException.class, () ->
            service.addResourceToFuseki(testUtil.createResource(), URI.create("https://test-broker.com/id/test/connector-producer")));
    assertEquals(errorMessage, exception.getMessage());
  }

  @Test
  void addResourceToFuseki_With1Catalog() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();
    Resource resource=testUtil.createResource();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    //assertThat(modelService.listResourcesTitle(graphName).size()).isEqualTo(0);

    // Add Resource
    String str=service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));
    System.out.println(str);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;

    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Asserts that the returned value (Location) is the expected
    String catalogName=jenaService.listConnectorCatalogs(graphName).stream().findAny().get().getObject().toString();
    System.out.println("catalogName: "+catalogName);

    assertEquals(str, FusekiUtil.buildNewSubjectName(catalogName, resource.getId()));

    //assertThat(modelService.listResourcesTitle(graphName).size()).isEqualTo(1);

  }

  /**
   * Test is the exact same as one on JenaServiceTest. Howver, this time the query is automatically rewritten by the service and the duplicated values disappeare
   * */
  @Test
  void fusekiGetConnectorsCatalogsOffers() {
    String sparqlQuery = "PREFIX idsa-core:     <https://w3id.org/idsa/core/>\n" +
        "PREFIX owl:           <http://www.w3.org/2002/07/owl#>\n" +
        "PREFIX rdf-syntax-ns: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        "SELECT ?connector ?connectorId ?connectorAccessUrl ?connectorTitle ?connectorDescription ?defaultEndpoint ?securityProfile ?publicKey ?resourceCatalog ?catalogUrl ?offer ?offerUrl ?offerVersion ?offerTitle ?offerDescription ?offerKeyWord ?offerCreated " +
        "?offerModified ?offerLanguage ?offerStandardLicense ?contractOfferUrl ?contractStart ?contractEnd ?provider ?consumer ?permission ?permissionUrl ?permissionTitle ?permissionDescription ?artifactUrl ?artifactCreationDate ?artifactfileName\n" +
        "WHERE {\n" +
        "  GRAPH ?__RESERVED {\n" +
        "    ?connector rdf-syntax-ns:type           <https://w3id.org/idsa/core/BaseConnector> ;\n" +
        "               owl:sameAs                   ?connectorId ;\n" +
        "               idsa-core:title              ?connectorTitle ;\n" +
        "               idsa-core:description        ?connectorDescription ;\n" +
        "               idsa-core:hasDefaultEndpoint ?defaultEndpoint ;\n" +
        "               idsa-core:securityProfile    ?securityProfile ;\n" +
        "               idsa-core:publicKey          ?publicKey ;\n" +
        "               idsa-core:resourceCatalog    ?resourceCatalog .\n" +
        "\n" +
        "   ?defaultEndpoint rdf-syntax-ns:type                     <https://w3id.org/idsa/core/ConnectorEndpoint> ;\n" +
        "                    <https://w3id.org/idsa/core/accessURL> ?connectorAccessUrl .\n" +
        "               \n" +
        "    ?resourceCatalog rdf-syntax-ns:type        <https://w3id.org/idsa/core/ResourceCatalog> ;\n" +
        "                     owl:sameAs                ?catalogUrl ;\n" +
        "                     idsa-core:offeredResource ?offer .\n" +
        "                     \n" +
        "    ?offer rdf-syntax-ns:type         <https://w3id.org/idsa/core/Resource> ;\n" +
        "           owl:sameAs                 ?offerUrl ;\n" +
        "           idsa-core:version          ?offerVersion ;\n" +
        "           idsa-core:title            ?offerTitle ;\n" +
        "           idsa-core:description      ?offerDescription ;\n" +
        "#           idsa-core:keyword          ?offerKeyWord ;\n" +
        "           idsa-core:created          ?offerCreated ;\n" +
        "           idsa-core:modified         ?offerModified ;\n" +
        "           idsa-core:language         ?offerLanguage ;\n" +
        "           idsa-core:standardLicense  ?offerStandardLicense ;\n" +
        "           idsa-core:resourceEndpoint ?resourceEndpoint ;\n" +
        "           idsa-core:contractOffer    ?contractOffer ;\n" +
        "           idsa-core:representation   ?representation .\n" +
        "    \n" +
        "    ?contractOffer rdf-syntax-ns:type      <https://w3id.org/idsa/core/ContractOffer> ;\n" +
        "                   owl:sameAs              ?contractOfferUrl ;\n" +
        "                   idsa-core:contractStart ?contractStart ;\n" +
        "                   idsa-core:contractDate  ?contractDate ;\n" +
        "                   idsa-core:contractEnd   ?contractEnd ;\n" +
        "                   idsa-core:provider      ?provider ;\n" +
        "#                   idsa-core:consumer      ?consumer ;\n" +
        "                   idsa-core:permission    ?permission .\n" +
        "    \n" +
        "    ?permission     rdf-syntax-ns:type    <https://w3id.org/idsa/core/Permission> ;\n" +
        "                    owl:sameAs            ?permissionUrl ;\n" +
        "                    idsa-core:title       ?permissionTitle ;\n" +
        "                    idsa-core:description ?permissionDescription .\n" +
        "    \n" +
        "    ?representation rdf-syntax-ns:type               <https://w3id.org/idsa/core/Representation> ;\n" +
        "                    owl:sameAs                       ?representationUrl ;\n" +
        "                    idsa-core:mediaType              ?representationMediaType ;\n" +
        "                    idsa-core:created                ?representationCreated ;\n" +
        "                    idsa-core:modified               ?representationModified ;\n" +
        "                    idsa-core:representationStandard ?representationStandard ;\n" +
        "                    idsa-core:instance               ?instance ;\n" +
        "                    idsa-core:language               ?representationlanguage .\n" +
        "                  \n" +
        "    ?instance rdf-syntax-ns:type     <https://w3id.org/idsa/core/Artifact> ;\n" +
        "              owl:sameAs             ?artifactUrl ;\n" +
        "              idsa-core:creationDate ?artifactCreationDate ;\n" +
        "              idsa-core:fileName     ?artifactfileName .\n" +
        "  }\n" +
        "}\n" +
        "ORDER BY ASC(?connectorId)";

    Connector subConn=testUtil.createConnectorSubcontracted();
    Connector prodConn=testUtil.createConnector();
    Resource producerResource= testUtil.createResource();

    // Update the Resource to make the contract offer hava a consumer and provider
    ContractOffer updatedContractOffer=testUtil.createContract();
    updatedContractOffer.setConsumer(subConn.getId());
    updatedContractOffer.setProvider(prodConn.getId());

    // Update the Representation to make the Representation have a Standard
    Representation updatedRepresentation=testUtil.createRepresentation();
    updatedRepresentation.setRepresentationStandard(URI.create("https://representation/standard"));
    updatedRepresentation.setInstance(List.of(testUtil.createArtefact()));

    // Update the Resource with the new Contract and Representation, and a standardLicense
    producerResource.setContractOffer(List.of(updatedContractOffer));
    producerResource.setRepresentation(List.of(updatedRepresentation));
    producerResource.setStandardLicense(URI.create("https://standard/license"));

    service.addConnectorToFuseki(prodConn);
    service.addResourceToFuseki(producerResource, prodConn.getId());


    // It needed the offerStandardLicense, representationStandard, provider, and consumer
    String result= service.executeSPARQLQuery(sparqlQuery);
    System.out.println(result);


    // Creates a list using the \n as the separator for the rows
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.toString().split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 2);

  }
  @Test
  void addResourceToFuseki_WithMultipleCatalogs() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnectorWithMultipleCatalogs();
    Resource resource=testUtil.createResource();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);
    //service.addConnectorToFuseki(connector);


    // Add Resource
    String resourceLocation=service.addResourceToFuseki(resource, conn.getId());

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;

    System.out.println(resourceLocation);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Asserts that the returned value (Location) is the expected
    assertEquals(resourceLocation, "http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692");

  }

  @Test
  void addResourceToFuseki_WithNoRule() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);

    // Create Resource with Artefact, Representation and Contract
    Resource resource=testUtil.createResourceWithIncompleteDataModel();
    Representation representation=testUtil.createRepresentation();
    representation.setInstance(List.of(testUtil.createArtefact())); // Add Artefact
    resource.setRepresentation(List.of(representation)); // Add resource
    resource.setContractOffer(List.of(testUtil.createIncompleteContract())); // Add Contract
    String errorMessage="Resource "+resource.getId()+" cannot be inserted on the Broker. It needs to have a Rule.";
    // Add Resource
    Exception exception = assertThrows(InvalidValueException.class, () ->
            service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer")));
    assertEquals(errorMessage, exception.getMessage());

  }

  @Test
  void addResourceToFuseki_WithNoRepresentation() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);
    //service.addConnectorToFuseki(connector);


    // Create Resource with Contract and Rule
    Resource resource=testUtil.createResourceWithIncompleteDataModel();
    ContractOffer contractOffer=testUtil.createContract();
    contractOffer.setPermission(List.of(testUtil.createPermission())); // Add Rule
    resource.setContractOffer(List.of(contractOffer)); // Add Contract

    String errorMessage="Resource "+resource.getId()+" cannot be inserted on the Broker. It needs to have a Representation.";
    // Add Resource
    Exception exception = assertThrows(InvalidValueException.class, () ->
            service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer")));
    assertEquals(errorMessage, exception.getMessage());

  }

  @Test
  void addResourceToFuseki_WithNoContract() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);

    // Create Resource with Artefac and Representation
    Resource resource=testUtil.createResourceWithIncompleteDataModel();
    Representation representation=testUtil.createRepresentation();
    representation.setInstance(List.of(testUtil.createArtefact())); // Add Artefact
    resource.setRepresentation(List.of(representation)); // // Add Representation

    String errorMessage="Resource "+resource.getId()+" cannot be inserted on the Broker. It needs to have a Contract.";
    // Add Resource
    Exception exception = assertThrows(InvalidValueException.class, () ->
            service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer")));
    assertEquals(errorMessage, exception.getMessage());

  }

  @Test
  void addResourceToFuseki_WithNoArtefact() {
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);

    // Create Resource with Representation, Rule and Contract
    Resource resource=testUtil.createResourceWithIncompleteDataModel();
    resource.setRepresentation(List.of(testUtil.createRepresentation())); // Add Representation
    ContractOffer contractOffer=testUtil.createContract();
    contractOffer.setPermission(List.of(testUtil.createPermission())); // Add Rule
    resource.setContractOffer(List.of(contractOffer)); // Add Contract

    String errorMessage="Resource "+resource.getId()+" cannot be inserted on the Broker. It needs to have a Artefact.";
    // Add Resource
    Exception exception = assertThrows(InvalidValueException.class, () ->
            service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer")));
    assertEquals(errorMessage, exception.getMessage());

  }

  @Test
  void addResourceToFuseki_AnArtefactWasRemoved() {
    //testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();
    FusekiConverter fusekiConverter=new FusekiConverter();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // Create the artefact to be removed
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2025, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);

    Artifact artifact=new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/resource02"))
            ._fileName_("test-file-2")
            ._checkSum_("0")
            ._creationDate_(createdDate)
            ._byteSize_(BigInteger.valueOf(0))
            .build();
    Representation representation=new RepresentationBuilder(URI.create("https://localhost:8080/localconnector/api/representations/resource02"))
            ._instance_(artifact)
            ._language_(Language.EN)
            ._mediaType_(
                    new IANAMediaTypeBuilder()._filenameExtension_("application/octet-stream")
                            .build())
            ._created_(createdDate)
            ._modified_(createdDate).build();

    // Create a Resource with 2 Artefact
    Resource resource=testUtil.createResource();
    List<Representation> representationList=resource.getRepresentation();
    representationList.add(representation);
    resource.setRepresentation(representationList);

    // Add Resource
    assertThat(resource.getRepresentation().size()).isEqualTo(2);
    String response =service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;

    // Checks that the response is the same as the graphName stored on the DB
    System.out.println(response);
    System.out.println(jenaService.getModel(graphName).getName());
    assertNotNull(jenaService.getModel(graphName));
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);


    // Retrive the model store on the DB
    ModelDto originalInsertedResource_2Artifacts = jenaService.getModel(graphName);

    LOGGER.info("Test starts here...");
    // Remove artifact from Resource
    representationList.remove(representation);
    resource.setRepresentation(representationList);
    assertThat(resource.getRepresentation().size()).isEqualTo(1);

    // Updates the resource
    String responseAfterRemovingArtifact =service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));
    assertThat(responseAfterRemovingArtifact).isEqualTo(response);

    // Retrive the updated model store on the and Save the mumber of lines
    ModelDto insertedResourceAfterRemovingArtifact = jenaService.getModel(graphName);
    System.err.println("Resource Model On DB After removing the Artefact");
    System.err.println(fusekiConverter.modelToTurleFormat(insertedResourceAfterRemovingArtifact.getModel()));

    // Checks that the Resource now stored on the DB does not contain the removed Artefact
    Optional<Statement> artifactSameAsStatementFromDB = ResourceUtil
            .getArtifactSameAsStatementFromDB(insertedResourceAfterRemovingArtifact, artifact.getId());
    assertThat(artifactSameAsStatementFromDB).isEmpty();

    // Checks that the model of the Resource now stored on the DB has less triplets than before updating the resource
    assertThat(insertedResourceAfterRemovingArtifact.getModel().listStatements().toList().size())
            .isLessThan(originalInsertedResource_2Artifacts.getModel().listStatements().toList().size());

  }

  @Test
  void addResourceToFuseki_AnArtefactWasRemoved_WithOneRepresentation() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();
    FusekiConverter fusekiConverter=new FusekiConverter();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // Create a Resource with 4 Artefact
    Resource resource=testUtil.createResourceWithMultipleArtefacts();

    // Add Resource
    assertThat(resource.getRepresentation().get(0).getInstance().size()).isEqualTo(4);
    String resourceDBLocation =service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));

    // Retrive the model store on the DB
    ModelDto originalInsertedResource_4Artifacts = jenaService.getModel(graphName);
    System.out.println(fusekiConverter.modelToTurleFormat(originalInsertedResource_4Artifacts.getModel()));
    // Checks that the Resource now stored on the DB contains 4 Artefacts
    List<Statement> artifactSameAsStatementFromDB = originalInsertedResource_4Artifacts.getModel().listStatements()
            .filterKeep(i->i.getObject().equals(ResourceFactory.createProperty(ARTIFACT))).toList();
    artifactSameAsStatementFromDB.forEach(System.out::println);
    assertThat(artifactSameAsStatementFromDB.size()).isEqualTo(4);

    LOGGER.info("Test starts here...");
    Artifact artifact=testUtil.createArtefact();
    // Remove artifact from Resource
    resource.getRepresentation().get(0).setInstance(List.of(
            artifact));
    assertThat(resource.getRepresentation().get(0).getInstance().size()).isEqualTo(1);

    // Updates the resource
    String responseAfterRemovingArtifact =service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));
    assertThat(responseAfterRemovingArtifact).isEqualTo(resourceDBLocation);

    // Retrive the updated model store on the DB
    ModelDto insertedResourceAfterRemovingArtifact = jenaService.getModel(graphName);
    System.err.println("Resource Model On DB After removing the Artefact");
    System.err.println(fusekiConverter.modelToTurleFormat(insertedResourceAfterRemovingArtifact.getModel()));

    // Checks that the Resource now stored on the DB only contains one Artefact
    artifactSameAsStatementFromDB.clear();
     artifactSameAsStatementFromDB = insertedResourceAfterRemovingArtifact.getModel().listStatements()
            .filterKeep(i->i.getObject().equals(ResourceFactory.createProperty(ARTIFACT))).toList();
    artifactSameAsStatementFromDB.forEach(System.out::println);
    assertThat(artifactSameAsStatementFromDB.size()).isEqualTo(1);

    // Checks that the model of the Resource now stored on the DB has less triplets than before updating the resource
    assertThat(insertedResourceAfterRemovingArtifact.getModel().listStatements().toList().size())
            .isLessThan(originalInsertedResource_4Artifacts.getModel().listStatements().toList().size());

  }

  @Test
  void addResourceToFuseki_ResourceIsAlreadyThere() {
    testUtil.removeAllGraphsFromDB(accessor);
    Connector conn= testUtil.createConnector();
    FusekiConverter fusekiConverter=new FusekiConverter();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // Create a Resource
    Resource resource=testUtil.createResource();

    // Add Resource
    String response =service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));

    // Retrive the model store on the DB
    ModelDto originalInsertedResource = jenaService.getModel(graphName);
    System.out.println(fusekiConverter.modelToTurleFormat(originalInsertedResource.getModel()));


    LOGGER.info("Test starts here...");
    // Updates the resource
    String responseAfterTryingToReinsertAResource =service.addResourceToFuseki(resource, URI.create("https://test-broker.com/id/test/connector-producer"));
    assertThat(responseAfterTryingToReinsertAResource).isEqualTo(response);

    // Retrive the updated model store on the DB
    ModelDto insertedResourceAfterUpdating = jenaService.getModel(graphName);
    System.err.println("Resource Model On DB After removing the Artefact");
    System.err.println(fusekiConverter.modelToTurleFormat(insertedResourceAfterUpdating.getModel()));

    // Checks that the Resource now stored on the DB is equal to the previous one
    assertThat(FusekiUtil.getStatementsDiff(
                    insertedResourceAfterUpdating.getModel(),
                    originalInsertedResource.getModel())
                .hasDiff())
            .isEqualTo(false);

  }

  /**
   * Test Adding various resources to Fuseki.
   * They are incremented on the resourceCatalog
   * */
  @Test
  void addMultipleResourcesToFuseki() {
    Connector conn= testUtil.createConnector();

    // Add Connector
    String conn_graphName=service.addConnectorToFuseki(conn);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Create Resources
    Resource resource=testUtil.createResource();
    Resource resource2=testUtil.createResource2();

    List<Statement> resourceListOnConnector=new ArrayList<>();
    // Get the number of Resources on the Database for the connector
    resourceListOnConnector.addAll(jenaService.getModel(conn_graphName).getModel().listStatements(null,
            null, ResourceFactory.createResource(RESOURCE)).toList());

    assertEquals(resourceListOnConnector.size(), 0);
    resourceListOnConnector.clear();

    // Add one Resource
    service.addResourceToFuseki(resource, conn.getId());
    // Get the number of Resources on the Database for the connector
    resourceListOnConnector.addAll(jenaService.getModel(conn_graphName).getModel().listStatements(null,
            null, ResourceFactory.createResource(RESOURCE)).toList());
    assertEquals(resourceListOnConnector.size(), 1);
    resourceListOnConnector.clear();

    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    service.addResourceToFuseki(resource2, conn.getId());
    // Get the number of Resources on the Database for the connector
    resourceListOnConnector.addAll(jenaService.getModel(conn_graphName).getModel().listStatements(null,
            null, ResourceFactory.createResource(RESOURCE)).toList());
    assertEquals(resourceListOnConnector.size(), 2);

    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  @Test
  void removeResourceFromFuseki() {
    testUtil.removeAllGraphsFromDB(accessor);
    modelService.removeConnectorGraphs();
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Resource resource=testUtil.createResource();
    Connector conn= testUtil.createConnector();

    FusekiConverter converter=new FusekiConverter();
    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // Get Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Get Connector Model - Before Resource
    ModelDto connectorModel= jenaService.getModel(graphName );

    // Add Resource
    service.addResourceToFuseki(resource, conn.getId());
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 1);

    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    //***************************************************************************
    Integer numberOfStatementsBeforeRemoving=jenaService.getModel(graphName ).getModel().listStatements().toList().size();
    System.out.println("numberOfStatementsBeforeRemoving: "+numberOfStatementsBeforeRemoving);

    // Remove Resource. This returns a Model without the Resource
    Model modelWithoutResource=service.removeResourceFromFuseki(conn.getId(), resource.getId());
    Integer numberOfStatementsAfterRemoving=jenaService.getModel(graphName ).getModel().listStatements().toList().size();
    System.out.println("numberOfStatementsAfterRemoving: "+numberOfStatementsAfterRemoving);



    System.err.println(converter.modelToTurleFormat(modelWithoutResource));
    System.out.println(FusekiUtil.getStatementsDiff(connectorModel.getModel(), modelWithoutResource));
    // Sees if the Model after removing the resource is the same as before adding the resource
    assertFalse(FusekiUtil.getStatementsDiff(connectorModel.getModel(), modelWithoutResource).hasDiff());

    System.out.println(converter.modelToTurleFormat(modelWithoutResource));
    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  @Test
  void removeResourceFromFuseki_MultipleArtefacts() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Resource resource=testUtil.createResourceWithMultipleArtefacts();
    Connector conn= testUtil.createConnector();

    FusekiConverter converter=new FusekiConverter();
    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // Get Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    adminGraph.getModel().listStatements().forEach(System.out::println);
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Get Connector Model - Before Resource
    ModelDto connectorModel= jenaService.getModel(graphName );

    // Add Resource
    service.addResourceToFuseki(resource, conn.getId());
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 1);

    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Remove Resource. This returns a Model without the Resource
    Model modelWithoutResource=service.removeResourceFromFuseki(conn.getId(), resource.getId());
    //System.err.println(converter.modelToTurleFormat(modelWithoutResource));
    System.out.println(FusekiUtil.getStatementsDiff(connectorModel.getModel(), modelWithoutResource));
    // Sees if the Model after removing the resource is the same as before adding the resource
    assertFalse(FusekiUtil.getStatementsDiff(connectorModel.getModel(), modelWithoutResource).hasDiff());

    System.out.println(converter.modelToTurleFormat(modelWithoutResource));
    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
  }

  @Test
  void removeResourceFromFuseki_WhenResourceIsNotRegistered() {
    testUtil.removeAllGraphsFromDB(accessor);
    modelService.removeConnectorGraphs();
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Resource resource=testUtil.createResource();
    Connector conn= testUtil.createConnector();

    // Add Connector
    service.addConnectorToFuseki(conn);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Remove a Resource that was not registered on the Broker. This throws an error
    Exception exception
            =assertThrows(ResourceNotFoundException.class, () -> service.removeResourceFromFuseki(conn.getId(), resource.getId()));
    assertEquals(exception.getMessage(), "Resource not found with ID : '"+resource.getId()+"'. Did you send a ResourceUpdateMessage yet?");
  }

  @Test
  void removeResourceFromFuseki_WhenConnectorIsNotActive() {
    Connector conn= testUtil.createConnector();
    Resource resource=testUtil.createResource();

    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);


    // Add Connector
    service.addConnectorToFuseki(conn);

    // GET Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Add Resource
    service.addResourceToFuseki(resource, conn.getId());

    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    // Assert that did not change anything on the Admin Graph
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // Add Resource. Throws an error since the connector is disabled
    Exception exception= assertThrows(ResourceNotFoundException.class, () ->
            service.removeResourceFromFuseki(conn.getId(), resource.getId()));
    assertEquals(exception.getMessage(), "Connector not found with ID '"+ conn.getId()+"'. Did you send a ConnectorUpdateMessage yet?");
  }


  @Test
  void makeConnectorUnavailable() {

    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    // GET Admin Graph
    LOGGER.info("Retrieving Admin Graph ...");
    ModelDto adminGraph=adminGraphService.getGraph();
    Integer numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    LOGGER.info("... Admin Graph retrieved!");
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 0);

    Connector conn= testUtil.createConnector();

    LOGGER.info("Adding a Connector to the Database ...");
    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);
    LOGGER.info("... Connector Added!");

    // GET Admin Graph
    LOGGER.info("Retrieving Admin Graph ...");
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    LOGGER.info("... Admin Graph retrieved!");

    //Check if the Connector was only added once
    //Check if the Connector is active
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName)).stream().findFirst().get().getObject().asLiteral().getBoolean(), true);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);

    // "Remove" the Connector
    LOGGER.info("Deactivating Connector...");
    service.makeConnectorUnavailable(conn.getId());
    // GET Admin Graph again (because the graph was updated)
    adminGraph=adminGraphService.getGraph();
    numberOfConnectorRegisteredOnAdminGraph=adminGraph.getModel().listStatements().toList().size()-1;
    //Check if the Connector is inactive
    assertEquals(adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName)).stream().findFirst().get().getObject().asLiteral().getBoolean(), false);
    assertEquals(numberOfConnectorRegisteredOnAdminGraph, 1);
    LOGGER.info("... Connector Deactivated.");
  }


  @Test
  void makeConnectorUnavailable_IfConnectorIsNotRegistered() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    String errorMessage="Connector not found with ID 'https://test-broker.com/id/test/connector-producer'. Did you send a ConnectorUpdateMessage yet?";
    // Tries to Remove Connector
    Exception exception = assertThrows(ResourceNotFoundException.class, () ->
            service.makeConnectorUnavailable(URI.create("https://test-broker.com/id/test/connector-producer")));
    assertEquals(errorMessage, exception.getMessage());
  }

  @Test
  void makeConnectorUnavailable_IfConnectorIsAlreadyInactive() {
    testUtil.removeAllGraphsFromDB(accessor);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);

    Connector conn= testUtil.createConnector();

    // Add Connector
    String graphName=service.addConnectorToFuseki(conn);

    // "Remove" the Connector
    adminGraphService.deactivateConnector(graphName);

    String errorMessage="The Connector you are trying to remove ('"+conn.getId()+"') was not found. Did you send a ConnectorUpdateMessage yet?";
    // Tries to Remove Connector
    Exception exception = assertThrows(InvalidValueException.class, () ->
            service.makeConnectorUnavailable(conn.getId()));
    assertEquals(errorMessage, exception.getMessage());

  }


  @Test
  void executeSPARQLQuery_SELECT() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/title>\t\"Dataspace Connector - CircThread - circthread-01\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/description>\t\"IDS Connector reference implementation\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/circthread-01/-1896932083/-39041446>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.7\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.1\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.7\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.2\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.1.0\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.4\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.5\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.3\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.1.2\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.0.0\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.0\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://circthread.com/ids/connector/id/circthread-01>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\t<https://w3id.org/idsa/core/version>\t\"8.0.8\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/PublicKey>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://w3id.org/idsa/autogen/publicKey/28a15737-b759-4acc-87c6-e3441b9418ec>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\t<https://w3id.org/idsa/core/keyType>\t<https://w3id.org/idsa/code/RSA>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\t<https://w3id.org/idsa/core/keyValue>\t\"VFVsSlFrbHFRVTVDWjJ0eGFHdHBSemwzTUVKQlVVVkdRVUZQUTBGUk9FRk5TVWxDUTJkTFEwRlJSVUYwUW01d2FtOWhibEZqYnpOU09UVnRVbFI1VUV0clMyeFRTR1JsV1hoMFZ6TjRTbXhhWTBkcmRVRmFkbkJEZW1rdmNFdzBhbUpNVFRGTVltY3pNbVF5WXpGcGRtOUtkMjFPTUN0ek9GbFJRMWxGVEVSa1RUQlJZM2N2T0VzM00wZFZZMDlSY1RoVGIzRlFlRE4wT0hGWVVEYzVibTk1U0dRd05TdHhWMEU1UjNCT2EwRlRPRXQyVDFOMmNtTktXRTR3UmtORVFtWjVhVVpEVG01NlZGb3pNamxhT0dWeFRUWkxjakJDV0hFNVYySjJVWGcyYlZobU9HVndURTlrT0VjNFpWRjJXV1V2Y0hkdmFVa3hNRnBTTjFST2MwZFRSV3B4TjFGNlkxUkJjamxMV0RSd1Z6Uk1VMGMwVmpaaE1WZDRZbEpyTUhwT2VHUlZNRmQzVlVWVGNuTm5hWEJpYmpoWVZUZDZWV28xTkdsQ1lXNUdZV1ZWTlRWaU5rUnBjSGxLT0dkeVpsaHlUU3N3T1dkaVRHeDVUV2x5Tm10NWFrUm9Ta0k1Y0ZCTVkwZG5TVXRoZG14SFJ5dDVkM0JoV1ZVdldIQk9abEZKUkVGUlFVST0=\"\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-39041446>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/ConnectorEndpoint>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-39041446>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://w3id.org/idsa/autogen/connectorEndpoint/88d6848f-893b-45f7-b013-8030b17e5129>\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-39041446>\t<https://w3id.org/idsa/core/accessURL>\t<https://host.docker.internal:8080/circthread-01/api/ids/data>\n";
    ArrayList<String> expectedResultAsList = new ArrayList<>(Arrays.asList(expectedResult.split("\n")));

    String sparqlQuery = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";
    /*
     * SELECT  ?subject ?predicate ?object WHERE { GRAPH ?__RESERVED { ?subject  ?predicate  ?object } }
     * */
    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.TSV);
    System.out.println(new String(result));

    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(result.split("\n")));
    //resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 30);
    System.err.println(resultAsList.containsAll(expectedResultAsList));
    System.err.println(resultAsList.contains(expectedResultAsList));
    System.err.println(resultAsList.toString().equals(expectedResult.toString()));
    assertTrue(resultAsList.containsAll(expectedResultAsList));
  }

  /**
   * Tests a SELECT where there are no active graphs
   * @return empty string
   */
  @Test
  void executeSPARQLQuery_SELECT_NoActiveGraphs() {
    // Deactivates any graph that may still be active from the previous tests
    adminGraphService.getGraph().getModel().listStatements().forEachRemaining(connectorGraph ->{
      adminGraphService.deactivateConnector(connectorGraph.getSubject().toString());
    });

    String sparqlQuery = "SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";

    // Checks that all connectors are inactive
    assertEquals(adminGraphService.getListOfActiveConnectors().size(), 0);

    // Sould return a empty string, since there are no active graphs
    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.TSV);
    assertEquals(result, "");
  }

  /**
   * Tests with a DESCRIBE Query
   * Should return a model in Turtle Format
   * */
  @Test
  void executeSPARQLQuery_DESCRIBE() {
    String lineSeparator=System.lineSeparator();
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Add Resource - To be on the ResultModel
    service.addResourceToFuseki(testUtil.createResource(), conn.getId());

    String expectedResult="@prefix ids: <https://w3id.org/idsa/core/> .\n" +
            "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\n" +
            "        a                    ids:ResourceCatalog ;\n" +
            "        owl:sameAs           <https://test-broker.com/id/test/catalog/api/connector-producer> ;\n" +
            "        ids:offeredResource  <http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692> .\n";

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> \n" +
            "DESCRIBE ?x\n" +
            "WHERE  {  ?x owl:sameAs <"+conn.getResourceCatalog().get(0).getId()+"> }";

    String result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));

    assertEquals(
            result.replaceAll("(.{64})", "$1\n").replaceAll("\n", "").replaceAll(lineSeparator, ""),
            expectedResult.replaceAll("\n", lineSeparator).replaceAll(lineSeparator, ""));
  }

  /**
   * Tests with a DESCRIBE Query
   * Should return a model
   * */
  @Test
  void executeSPARQLQuery_DESCRIBE_WithDisabledConnector() {
    String lineSeparator=System.lineSeparator();
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    /**
     * Test DESCRIBE with both Connectors active. Should return a model with 2 sub-models
     * */
    String expectedResult="@prefix ids:  <https://w3id.org/idsa/core/> .\n" +
        "@prefix idsc: <https://w3id.org/idsa/code/> .\n" +
        "@prefix owl:  <http://www.w3.org/2002/07/owl#> .\n" +
        "\n" +
        "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\n" +
        "        a             ids:PublicKey ;\n" +
        "        owl:sameAs    <https://test-broker.com/id/test/publicKey/dhiasdcna> ;\n" +
        "        ids:keyType   idsc:RSA ;\n" +
        "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
        "\n" +
        "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\n" +
        "        a             ids:PublicKey ;\n" +
        "        owl:sameAs    <https://w3id.org/idsa/autogen/publicKey/28a15737-b759-4acc-87c6-e3441b9418ec> ;\n" +
        "        ids:keyType   idsc:RSA ;\n" +
        "        ids:keyValue  \"VFVsSlFrbHFRVTVDWjJ0eGFHdHBSemwzTUVKQlVVVkdRVUZQUTBGUk9FRk5TVWxDUTJkTFEwRlJSVUYwUW01d2FtOWhibEZqYnpOU09UVnRVbFI1VUV0clMyeFRTR1JsV1hoMFZ6TjRTbXhhWTBkcmRVRmFkbkJEZW1rdmNFdzBhbUpNVFRGTVltY3pNbVF5WXpGcGRtOUtkMjFPTUN0ek9GbFJRMWxGVEVSa1RUQlJZM2N2T0VzM00wZFZZMDlSY1RoVGIzRlFlRE4wT0hGWVVEYzVibTk1U0dRd05TdHhWMEU1UjNCT2EwRlRPRXQyVDFOMmNtTktXRTR3UmtORVFtWjVhVVpEVG01NlZGb3pNamxhT0dWeFRUWkxjakJDV0hFNVYySjJVWGcyYlZobU9HVndURTlrT0VjNFpWRjJXV1V2Y0hkdmFVa3hNRnBTTjFST2MwZFRSV3B4TjFGNlkxUkJjamxMV0RSd1Z6Uk1VMGMwVmpaaE1WZDRZbEpyTUhwT2VHUlZNRmQzVlVWVGNuTm5hWEJpYmpoWVZUZDZWV28xTkdsQ1lXNUdZV1ZWTlRWaU5rUnBjSGxLT0dkeVpsaHlUU3N3T1dkaVRHeDVUV2x5Tm10NWFrUm9Ta0k1Y0ZCTVkwZG5TVXRoZG14SFJ5dDVkM0JoV1ZVdldIQk9abEZKUkVGUlFVST0=\" .";

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "DESCRIBE ?x\n" +
            "WHERE  {  ?x ids:keyType idsc:RSA }";

    String result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));

    assertEquals(
            result.replaceAll("(.{64})", "$1\n").replaceAll("\n", "").replaceAll(lineSeparator, ""),
            expectedResult.replaceAll("\n", lineSeparator).replaceAll(lineSeparator, ""));

    /**
     * Deactivate a Connector
     * Test DESCRIBE with only one active Connector. Should return a model with 1 sub-model
     * */

    // Disable Connector. Now only one Public Key should be returned
    service.makeConnectorUnavailable(conn.getId());
    expectedResult="@prefix ids:  <https://w3id.org/idsa/core/> .\n" +
            "@prefix idsc: <https://w3id.org/idsa/code/> .\n" +
            "@prefix owl:  <http://www.w3.org/2002/07/owl#> .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240>\n" +
            "        a             ids:PublicKey ;\n" +
            "        owl:sameAs    <https://w3id.org/idsa/autogen/publicKey/28a15737-b759-4acc-87c6-e3441b9418ec> ;\n" +
            "        ids:keyType   idsc:RSA ;\n" +
            "        ids:keyValue  \"VFVsSlFrbHFRVTVDWjJ0eGFHdHBSemwzTUVKQlVVVkdRVUZQUTBGUk9FRk5TVWxDUTJkTFEwRlJSVUYwUW01d2FtOWhibEZqYnpOU09UVnRVbFI1VUV0clMyeFRTR1JsV1hoMFZ6TjRTbXhhWTBkcmRVRmFkbkJEZW1rdmNFdzBhbUpNVFRGTVltY3pNbVF5WXpGcGRtOUtkMjFPTUN0ek9GbFJRMWxGVEVSa1RUQlJZM2N2T0VzM00wZFZZMDlSY1RoVGIzRlFlRE4wT0hGWVVEYzVibTk1U0dRd05TdHhWMEU1UjNCT2EwRlRPRXQyVDFOMmNtTktXRTR3UmtORVFtWjVhVVpEVG01NlZGb3pNamxhT0dWeFRUWkxjakJDV0hFNVYySjJVWGcyYlZobU9HVndURTlrT0VjNFpWRjJXV1V2Y0hkdmFVa3hNRnBTTjFST2MwZFRSV3B4TjFGNlkxUkJjamxMV0RSd1Z6Uk1VMGMwVmpaaE1WZDRZbEpyTUhwT2VHUlZNRmQzVlVWVGNuTm5hWEJpYmpoWVZUZDZWV28xTkdsQ1lXNUdZV1ZWTlRWaU5rUnBjSGxLT0dkeVpsaHlUU3N3T1dkaVRHeDVUV2x5Tm10NWFrUm9Ta0k1Y0ZCTVkwZG5TVXRoZG14SFJ5dDVkM0JoV1ZVdldIQk9abEZKUkVGUlFVST0=\" .\n" +
            "\n";

    result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));

    assertEquals(
            result.replaceAll("(.{64})", "$1\n").replaceAll("\n", "").replaceAll(lineSeparator, ""),
            expectedResult.replaceAll("\n", lineSeparator).replaceAll(lineSeparator, ""));
  }

  /**
   * Tests with a DESCRIBE Query
   * No active Connectors
   * Should return null
   * */
  @Test
  void executeSPARQLQuery_WithNoActiveConnectors_DESCRIBE_CONSTRUCT_ASK_SELECT() {
    LOGGER.info("Removing all graphs...");
    testUtil.removeAllGraphsFromDB(accessor);
    LOGGER.info("... Graphs removed");
    adminGraphService.getListOfActiveConnectors().forEach(System.out::println);
    Connector conn= testUtil.createConnector();

    // Add Connectors
    LOGGER.info("Adding Connectors...");
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);
    LOGGER.info("... Connectors added.");

    // Disable Connectors
    LOGGER.info("Disabling Connectors...");
    service.makeConnectorUnavailable(conn.getId());
    service.makeConnectorUnavailable(connector.getId());
    LOGGER.info("... Connectors disabled.");
    adminGraphService.getListOfActiveConnectors().forEach(System.out::println);

    // Checks that there are no active graphs
    assertEquals(adminGraphService.getListOfActiveConnectors().size(), 0);


    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/> PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "DESCRIBE ?x WHERE  {  ?x ids:keyType idsc:RSA }";
    assertEquals(service.executeSPARQLQuery(sparqlQuery), "");

    sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "ASK WHERE  {  ?x ids:keyType idsc:RSA }";
    assertEquals(service.executeSPARQLQuery(sparqlQuery), "");

    sparqlQuery = "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject  ?predicate  ?object }";
    assertEquals(service.executeSPARQLQuery(sparqlQuery), "");

    sparqlQuery = "SELECT ?subject ?predicate ?object WHERE { ?subject  ?predicate  ?object }";
    assertEquals(service.executeSPARQLQuery(sparqlQuery), "");

  }

  /**
   * Tests with an ASK Query
   * Should return a boolean
   * */
  @Test
  void executeSPARQLQuery_ASK() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Assert that the Connector with the ID exists
    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids:   <https://w3id.org/idsa/core/>\n" +
            "PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "ASK WHERE  {  ?x owl:sameAs <"+conn.getId()+"> }";

    String result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));
    assertTrue(Boolean.parseBoolean(result));

    // Assert that the Connector with the ID 'https://test-broker.com/id/test/connector-subcontracted' does NOT exist
    sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids:   <https://w3id.org/idsa/core/>\n" +
            "PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "ASK WHERE  {  ?x owl:sameAs <https://test-broker.com/id/test/connector-subcontracted> }";

    result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));

    assertFalse(Boolean.parseBoolean(result));
  }

  /**
   * Tests with an ASK Query
   * Should return a boolean
   * */
  @Test
  void executeSPARQLQuery_ASK_NotExist() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids:   <https://w3id.org/idsa/core/>\n" +
            "PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "ASK WHERE  {  ?x ids:description       \"IDS Connector reference implementation\" }";

    // Assert that the Connector with the description above exists ( It is enabled)
    String result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));
    assertTrue(Boolean.parseBoolean(result));

    // Disable Connector that has the field that is being asked
    service.makeConnectorUnavailable(connector.getId());
    // Execute Again. This time it should be false
    result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));
    assertFalse(Boolean.parseBoolean(result));
  }

  @Test
  void executeSPARQLQuery_CONSTRUCT() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="{\n" +
        "  \"@graph\" : [ {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/circthread-01/-1896932083\",\n" +
        "    \"@type\" : \"https://w3id.org/idsa/core/BaseConnector\",\n" +
        "    \"sameAs\" : \"https://circthread.com/ids/connector/id/circthread-01\",\n" +
        "    \"curator\" : \"https://www.inesctec.pt/\",\n" +
        "    \"description\" : \"IDS Connector reference implementation\",\n" +
        "    \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/circthread-01/-1896932083/-39041446\",\n" +
        "    \"inboundModelVersion\" : [ \"4.1.2\", \"4.2.6\", \"4.2.5\", \"4.0.0\", \"4.2.4\", \"4.1.0\", \"4.2.3\", \"4.2.2\", \"4.2.1\", \"4.2.0\", \"4.2.7\" ],\n" +
        "    \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
        "    \"outboundModelVersion\" : \"4.2.7\",\n" +
        "    \"publicKey\" : \"http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240\",\n" +
        "    \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
        "    \"title\" : \"Dataspace Connector - CircThread - circthread-01\",\n" +
        "    \"version\" : \"8.0.8\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/circthread-01/-1896932083/-1181550240\",\n" +
        "    \"@type\" : \"https://w3id.org/idsa/core/PublicKey\",\n" +
        "    \"sameAs\" : \"https://w3id.org/idsa/autogen/publicKey/28a15737-b759-4acc-87c6-e3441b9418ec\",\n" +
        "    \"keyType\" : \"https://w3id.org/idsa/code/RSA\",\n" +
        "    \"keyValue\" : \"VFVsSlFrbHFRVTVDWjJ0eGFHdHBSemwzTUVKQlVVVkdRVUZQUTBGUk9FRk5TVWxDUTJkTFEwRlJSVUYwUW01d2FtOWhibEZqYnpOU09UVnRVbFI1VUV0clMyeFRTR1JsV1hoMFZ6TjRTbXhhWTBkcmRVRmFkbkJEZW1rdmNFdzBhbUpNVFRGTVltY3pNbVF5WXpGcGRtOUtkMjFPTUN0ek9GbFJRMWxGVEVSa1RUQlJZM2N2T0VzM00wZFZZMDlSY1RoVGIzRlFlRE4wT0hGWVVEYzVibTk1U0dRd05TdHhWMEU1UjNCT2EwRlRPRXQyVDFOMmNtTktXRTR3UmtORVFtWjVhVVpEVG01NlZGb3pNamxhT0dWeFRUWkxjakJDV0hFNVYySjJVWGcyYlZobU9HVndURTlrT0VjNFpWRjJXV1V2Y0hkdmFVa3hNRnBTTjFST2MwZFRSV3B4TjFGNlkxUkJjamxMV0RSd1Z6Uk1VMGMwVmpaaE1WZDRZbEpyTUhwT2VHUlZNRmQzVlVWVGNuTm5hWEJpYmpoWVZUZDZWV28xTkdsQ1lXNUdZV1ZWTlRWaU5rUnBjSGxLT0dkeVpsaHlUU3N3T1dkaVRHeDVUV2x5Tm10NWFrUm9Ta0k1Y0ZCTVkwZG5TVXRoZG14SFJ5dDVkM0JoV1ZVdldIQk9abEZKUkVGUlFVST0=\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/circthread-01/-1896932083/-39041446\",\n" +
        "    \"@type\" : \"https://w3id.org/idsa/core/ConnectorEndpoint\",\n" +
        "    \"sameAs\" : \"https://w3id.org/idsa/autogen/connectorEndpoint/88d6848f-893b-45f7-b013-8030b17e5129\",\n" +
        "    \"accessURL\" : \"https://host.docker.internal:8080/circthread-01/api/ids/data\"\n" +
        "  } ],\n" +
        "  \"@context\" : {\n" +
        "    \"inboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
        "    },\n" +
        "    \"publicKey\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"title\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
        "    },\n" +
        "    \"securityProfile\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"maintainer\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"version\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/version\"\n" +
        "    },\n" +
        "    \"sameAs\" : {\n" +
        "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"hasDefaultEndpoint\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"outboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
        "    },\n" +
        "    \"curator\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"description\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
        "    },\n" +
        "    \"accessURL\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/accessURL\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"keyValue\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyValue\"\n" +
        "    },\n" +
        "    \"keyType\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyType\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    }\n" +
        "  }\n" +
        "}\n";

    String sparqlQuery = "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject  ?predicate  ?object }";

    String result=service.executeSPARQLQuery(sparqlQuery);
    System.out.println(new String(result));

    //assertThat(CompareUtil.compareJsonStrings(result, expectedResult)).isTrue();
    assertEquals(
            TestUtil.replaceBlankSpacesWithOneSpace(result),
            TestUtil.replaceBlankSpacesWithOneSpace(expectedResult
                    .replaceAll("\n", System.lineSeparator()))
    );

  }

  @Test
  void executeSPARQLQuery_ERROR() {
    String errorMessage="Query type is not valid. Valid values are: ASK, SELECT, CONSTRUCT and DESCRIBE";

    String sparqlQuery = "DELETE { ?s ?p ?o . } WHERE { ?s ?p ?o  }";

    Exception exception = assertThrows(BadRequestException.class, () ->
            service.executeSPARQLQuery(sparqlQuery));
    assertEquals(errorMessage, exception.getMessage());
  }

  @Test
  void executeSPARQLQuery_SELECT_WithReturnType_CSV() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="subject,predicate,object\n" +
            "http://test-hostname:8082/connectors/circthread-01/-1896932083,https://w3id.org/idsa/core/title,Dataspace Connector - CircThread - circthread-01\n";

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>" +
            "SELECT  ?subject ?predicate ?object\n"+
            "WHERE { " +
            "             ?subject a ids:BaseConnector. " +
            "             ?subject ?predicate ?object " +
            "             FILTER (regex(str(?predicate), 'title','i')) \n" +
            "}";

    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.CSV);
    System.out.println(new String(result));

    assertEquals(TestUtil.normalize(result), TestUtil.normalize(expectedResult));
  }

  @Test
  void executeSPARQLQuery_SELECT_WithReturnType_JSON() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="{ \"head\": {\n" +
            "    \"vars\": [ \"subject\" , \"predicate\" , \"object\" ]\n" +
            "  } ,\n" +
            "  \"results\": {\n" +
            "    \"bindings\": [\n" +
            "      { \n" +
            "        \"subject\": { \"type\": \"uri\" , \"value\": \"http://test-hostname:8082/connectors/circthread-01/-1896932083\" } ,\n" +
            "        \"predicate\": { \"type\": \"uri\" , \"value\": \"https://w3id.org/idsa/core/title\" } ,\n" +
            "        \"object\": { \"type\": \"literal\" , \"value\": \"Dataspace Connector - CircThread - circthread-01\" }\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}\n";

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>" +
            "SELECT  ?subject ?predicate ?object\n"+
            "WHERE { " +
            "             ?subject a ids:BaseConnector. " +
            "             ?subject ?predicate ?object " +
            "             FILTER (regex(str(?predicate), 'title','i')) \n" +
            "}";

    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.JSON);
    System.out.println(new String(result));

    assertEquals(TestUtil.normalize(result), TestUtil.normalize(expectedResult));
  }

  @Test
  void executeSPARQLQuery_SELECT_WithReturnType_Text() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="--------------------------------------------------------------------------------------------------------------------------------------------------------------\n" +
            "| subject                                                          | predicate                          | object                                             |\n" +
            "==============================================================================================================================================================\n" +
            "| <http://test-hostname:8082/connectors/circthread-01/-1896932083> | <https://w3id.org/idsa/core/title> | \"Dataspace Connector - CircThread - circthread-01\" |\n" +
            "--------------------------------------------------------------------------------------------------------------------------------------------------------------\n";

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>" +
            "SELECT  ?subject ?predicate ?object\n"+
            "WHERE { " +
            "             ?subject a ids:BaseConnector. " +
            "             ?subject ?predicate ?object " +
            "             FILTER (regex(str(?predicate), 'title','i')) \n" +
            "}";

    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.TEXT);
    System.out.println(new String(result));

    assertEquals(TestUtil.normalize(result), TestUtil.normalize(expectedResult));
  }

  @Test
  void executeSPARQLQuery_CONSTRUCT_WithReturnType_TURTLE() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="@prefix ids: <https://w3id.org/idsa/core/> .\n" +
            "\n" +
            "<http://test-hostname:8082/connectors/circthread-01/-1896932083>\n" +
            "        ids:title  \"Dataspace Connector - CircThread - circthread-01\" .\n";

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>" +
            "             CONSTRUCT { ?subject ?predicate ?object} " +
            "             WHERE { " +
            "                       ?subject a ids:BaseConnector. \n" +
            "                       ?subject ?predicate ?object \n" +
            "                       FILTER (regex(str(?predicate), 'title','i')) }";

    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.TURTLE);
    System.out.println(new String(result));

    assertEquals(
            TestUtil.replaceBlankSpacesWithOneSpace(result),
            TestUtil.replaceBlankSpacesWithOneSpace(expectedResult
                    .replaceAll("\n", System.lineSeparator()))
    );

  }

  @Test
  void executeSPARQLQuery_CONSTRUCT_WithReturnType_TRIPLES() {
    Connector conn= testUtil.createConnector();

    // Add Connectors
    service.addConnectorToFuseki(conn);
    service.addConnectorToFuseki(connector);

    // Disable Connector
    service.makeConnectorUnavailable(conn.getId());

    String expectedResult="<http://test-hostname:8082/connectors/circthread-01/-1896932083> <https://w3id.org/idsa/core/title> \"Dataspace Connector - CircThread - circthread-01\" .\n";

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>" +
            "             CONSTRUCT { ?subject ?predicate ?object} " +
            "             WHERE { " +
            "                       ?subject a ids:BaseConnector. \n" +
            "                       ?subject ?predicate ?object \n" +
            "                       FILTER (regex(str(?predicate), 'title','i')) }";

    String result=service.executeSPARQLQuery(sparqlQuery, ResponseType.TRIPLES);
    System.out.println(new String(result));

    assertEquals(
            TestUtil.replaceBlankSpacesWithOneSpace(result),
            TestUtil.replaceBlankSpacesWithOneSpace(expectedResult
                    .replaceAll("\n", System.lineSeparator()))
    );

  }

  @Test
  void executeSPARQLQuery_SELECT_WithReturnType() {
    Connector conn= testUtil.createConnector();
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();


    // Register Connectors on DB
    String graphNameProducer =service.addConnectorToFuseki(conn);
    String graphNameSubcontracted=service.addConnectorToFuseki(connectorSubcontracted);


    // Register Resources on DB
    service.addResourceToFuseki(testUtil.createResource(), conn.getId());
    service.addResourceToFuseki(testUtil.createResource2(), connectorSubcontracted.getId());

    service.makeConnectorUnavailable(connectorSubcontracted.getId());

    String expectedTSV="?subject\t?predicate\t?object\n" +
        "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"\n";
    String expectedCSV="subject,predicate,object\n" +
        "http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692,https://w3id.org/idsa/core/title,PDP-P20230303\n";
    String expectedJSON="{ \"head\": {\n" +
        "    \"vars\": [ \"subject\" , \"predicate\" , \"object\" ]\n" +
        "  } ,\n" +
        "  \"results\": {\n" +
        "    \"bindings\": [\n" +
        "      { \n" +
        "        \"subject\": { \"type\": \"uri\" , \"value\": \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692\" } ,\n" +
        "        \"predicate\": { \"type\": \"uri\" , \"value\": \"https://w3id.org/idsa/core/title\" } ,\n" +
        "        \"object\": { \"type\": \"literal\" , \"value\": \"PDP-P20230303\" }\n" +
        "      }\n" +
        "    ]\n" +
        "  }\n" +
        "}\n";
    String expectedText="-----------------------------------------------------------------------------------------------------------------------------------------------------\n" +
        "| subject                                                                                    | predicate                          | object          |\n" +
        "=====================================================================================================================================================\n" +
        "| <http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692> | <https://w3id.org/idsa/core/title> | \"PDP-P20230303\" |\n" +
        "-----------------------------------------------------------------------------------------------------------------------------------------------------\n";

    String sparqlQuery = "PREFIX ids: <https://w3id.org/idsa/core/>" +
        "SELECT  ?subject ?predicate ?object\n"+
        "WHERE { GRAPH ?__RESERVED { " +
        "             ?subject a ids:Resource. " +
        "             ?subject ?predicate ?object " +
        "             FILTER (regex(str(?predicate), 'title','i')) \n" +
        "}}";

    LOGGER.info("Test method using TSV as the response format...");
    String result= service.executeSPARQLQuery(sparqlQuery, ResponseType.TSV);
    System.out.println(result);
    assertEquals(result, expectedTSV);
    LOGGER.info("... Test was successfull");

    LOGGER.info("Test method using CSV as the response format...");
    result= service.executeSPARQLQuery(sparqlQuery, ResponseType.CSV);
    System.out.println(result.replaceAll("\n", System.lineSeparator()));
    assertEquals(
        TestUtil.normalize(result),
        TestUtil.normalize(expectedCSV));
    LOGGER.info("... Test was successfull");

    LOGGER.info("Test method using JSON as the response format...");
    result= service.executeSPARQLQuery(sparqlQuery, ResponseType.JSON);
    System.out.println(result);
    assertEquals(result, expectedJSON);
    LOGGER.info("... Test was successfull");

    LOGGER.info("Test method using TEXT as the response format...");
    result= service.executeSPARQLQuery(sparqlQuery, ResponseType.TEXT);
    System.out.println(result);
    assertEquals(result, expectedText);
    LOGGER.info("... Test was successfull");

  }




}