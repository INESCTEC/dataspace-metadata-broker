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
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.util.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.FusekiConstants.SEPARATOR;


/**
 * Test Class that tests if the output of the SELECT, DESCRIBE, ASK and CONSTRUCT is correct
 * Also tests the error message
 *
 * */
@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class JenaServiceTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(JenaServiceTest.class);
  @Autowired
  FusekiConnectionAccessor accessor;

  TestUtil testUtil;

  ModelService modelService;
  BrokerService brokerService;
  JenaService jenaService;

  FusekiConverter fusekiConverter;

  Connector connectorProducer;
  Connector connectorSubcontracted;
  Connector connectorProducerMultipleCatalogs;
  String graphNameProducer;
  String graphNameSubcontracted;
  String selectReturnType= ResponseType.TSV;
  Resource producerResource;


  @BeforeEach
  void setUp() {
    modelService =new ModelService(accessor);
    brokerService=new BrokerService(accessor);
    jenaService =new JenaService(accessor);

    testUtil=new TestUtil();
    fusekiConverter=new FusekiConverter();

    testUtil.removeAllGraphsFromDB(accessor);
    
    connectorProducer=testUtil.createConnector();
    connectorSubcontracted=testUtil.createConnectorSubcontracted();
    connectorProducerMultipleCatalogs=testUtil.createConnectorWithMultipleCatalogs();

    //Adds Data
    // initialize Resources
    producerResource=testUtil.createResource();
    Resource subcontractedResource=testUtil.createResource2();

    // Register Connectors on DB
    graphNameProducer =brokerService.addConnectorToFuseki(connectorProducer);
    graphNameSubcontracted=brokerService.addConnectorToFuseki(connectorSubcontracted);


    // Register Resources on DB
    brokerService.addResourceToFuseki(producerResource, connectorProducer.getId());
    brokerService.addResourceToFuseki(subcontractedResource, connectorSubcontracted.getId());
  }

  @Test
  void select()  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    String sparqlQuery = "SELECT  ?subject ?predicate ?object\n"+
            "FROM NAMED <"+graphNameProducer+"> FROM NAMED <"+graphNameSubcontracted+">\n" +
            "WHERE { GRAPH ?__RESERVED { ?subject  ?predicate  ?object }}";

    // Get the ResultSet
    ResultSet result= jenaService.select(QueryUtil.constructQuery(sparqlQuery));
    // Convert the ResultSet to TSV
    ResultSetFormatter.outputAsTSV(outputStream, result);

    // Creates a list using the \n as the separator for the rows
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(outputStream.toString().split("\n")));
    //resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 127);
  }

  @Test
  void construct() {

    // Remove the Subcontracted Resource, since the Query will only query the Subcontracted. This way is safer to compare since no automatic IDs are generated
    brokerService.removeResourceFromFuseki(connectorSubcontracted.getId(), testUtil.createResource2().getId());

    String expectedStatements="[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/catalog/api/connector-subcontracted]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ResourceCatalog]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, https://w3id.org/idsa/core/keyValue, \"S2V5VmFsdWVFeGFtcGxl\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, https://w3id.org/idsa/core/keyType, https://w3id.org/idsa/code/RSA]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/publicKey/gtedcsd]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/PublicKey]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950, https://w3id.org/idsa/core/accessURL, https://host.docker.internal:8080/test/subcontracted/api/ids/data]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/connectorEndpoint/gtedcsd]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ConnectorEndpoint]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/outboundModelVersion, \"4.2.6\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/securityProfile, https://w3id.org/idsa/code/BASE_SECURITY_PROFILE]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/BaseConnector]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/description, \"Description\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/title, \"Title\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/publicKey, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/resourceCatalog, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/connector-subcontracted]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.6\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/hasDefaultEndpoint, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950]";
    String sparqlQuery = "CONSTRUCT { ?subject ?predicate ?object}\n"+
            "FROM NAMED <"+graphNameSubcontracted+">\n" +
            "WHERE { GRAPH ?__RESERVED { ?subject  ?predicate  ?object }}";

    // Execute Query
    Model result= jenaService.construct(QueryUtil.constructQuery(sparqlQuery));

    result.listStatements().forEach(System.out::println);

    assertEquals(result.listStatements().toList().size(), 21);
    List<String> resultAsList = new ArrayList<>();
    result.listStatements().forEach(statement -> resultAsList.add(statement.toString()));
    List<String> expectedStatementsAsList = new ArrayList<>(Arrays.asList(expectedStatements.toString().split("\n")));

    assertTrue(CompareUtil.compareStrings(resultAsList, expectedStatementsAsList));
  }

  @Test
  void describe() {

    String expectedModel="[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/outboundModelVersion, \"4.2.6\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/securityProfile, https://w3id.org/idsa/code/BASE_SECURITY_PROFILE]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/BaseConnector]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/description, \"Description\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/title, \"Title\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/resourceCatalog, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/publicKey, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/connector-subcontracted]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.6\"]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/graphIsActive, \"true\"^^http://www.w3.org/2001/XMLSchema#boolean]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/]\n" +
            "[http://test-hostname:8082/connectors/connector-subcontracted/-821347780, https://w3id.org/idsa/core/hasDefaultEndpoint, http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950]";
    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "            DESCRIBE ?x \n" +
            "            WHERE  {  GRAPH ?__RESERVED { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }}";

    // Execute Query
    Model result= jenaService.describe(QueryUtil.constructQuery(sparqlQuery));

    //System.out.println(fusekiConverter.modelToTurleFormat(result));
    result.listStatements().forEach(System.out::println);

    assertEquals(result.listStatements().toList().size(), 13);

    // Convert output into lists
    List<String> resultAsList = new ArrayList<>();
    result.listStatements().forEach(statement -> resultAsList.add(statement.toString()));
    List<String> expectedStatementsAsList = new ArrayList<>(Arrays.asList(expectedModel.toString().split("\n")));

    // Compare
    assertTrue(CompareUtil.compareStrings(resultAsList, expectedStatementsAsList));
    //assertEquals(result.toString(), expectedModel.replaceAll("\n", System.lineSeparator()));
  }

  @Test
  void ask() {

    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids:   <https://w3id.org/idsa/core/>\n" +
            "PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
            "ASK WHERE  {  GRAPH ?__RESERVED  { ?x owl:sameAs <"+connectorSubcontracted.getId()+"> }}";

    // Execute Query
    boolean result= jenaService.ask(QueryUtil.constructQuery(sparqlQuery));

    System.out.println(sparqlQuery);
    System.out.println("Query result: "+result);

    assertTrue(result);
  }

  @Test
  void fusekiGetConnectors() {
    LOGGER.info("Start test...");
    String sparqlQuery = "PREFIX idsa-core:     <https://w3id.org/idsa/core/>\n" +
            "PREFIX owl:           <http://www.w3.org/2002/07/owl#>\n" +
            "PREFIX rdf-syntax-ns: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
            "SELECT ?connector ?connectorId ?connectorAccessUrl ?connectorTitle ?connectorDescription ?defaultEndpoint ?securityProfile ?publicKey ?resourceCatalog\n" +
            "FROM NAMED <"+graphNameProducer+"> FROM NAMED <"+graphNameSubcontracted+">\n" +
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
            "  }\n" +
            "}\n" +
            "ORDER BY ASC(?connectorId)";
    ResultSet result= jenaService.select(QueryUtil.constructQuery(sparqlQuery));

    ByteArrayOutputStream outputStream=converSelectOutputToTSV(result);
    // Creates a list using the \n as the separator for the rows
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(outputStream.toString().split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 3);
    LOGGER.info("... Test passed.");
  }


  @Test
  void fusekiGetCatalogs() {
    List<String> expectResult=new ArrayList<>();
    expectResult.add("?resourceCatalog\t?catalogUrl\t?offeredResource");
    expectResult.add("<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\t<https://test-broker.com/id/test/catalog/api/connector-producer>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>");
    expectResult.add("<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561>\t<https://test-broker.com/id/test/catalog/api/connector-subcontracted>\t<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-1066753158>");

    String sparqlQuery = "PREFIX idsa-core:     <https://w3id.org/idsa/core/>\n" +
            "PREFIX owl:           <http://www.w3.org/2002/07/owl#>\n" +
            "PREFIX rdf-syntax-ns: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
            "SELECT ?resourceCatalog ?catalogUrl ?offeredResource \n" +
            "WHERE {\n" +
            "  GRAPH ?__RESERVED {\n" +
            "    ?resourceCatalog rdf-syntax-ns:type        <https://w3id.org/idsa/core/ResourceCatalog> ;\n" +
            "                     owl:sameAs                ?catalogUrl ;\n" +
            "                     idsa-core:offeredResource ?offeredResource  .\n" +
            "  }\n" +
            "}\n" +
            "ORDER BY ASC(?catalogUrl)";
    ResultSet result= jenaService.select(QueryUtil.constructQuery(sparqlQuery));
    //System.out.println(result);

    ByteArrayOutputStream outputStream=converSelectOutputToTSV(result);

    // Creates a list using the \n as the separator for the rows
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(outputStream.toString().split("\n")));
    resultAsList.forEach(System.out::println);

    assertEquals(resultAsList.size(), 3);
    assertTrue(CompareUtil.compareStrings(resultAsList, expectResult));

  }


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

    // Update the Resource to make the contract offer hava a consumer and provider
    ContractOffer updatedContractOffer=testUtil.createContract();
    updatedContractOffer.setConsumer(connectorSubcontracted.getId());
    updatedContractOffer.setProvider(connectorProducer.getId());
    producerResource.setContractOffer(List.of(updatedContractOffer));

    // Update the Representation to make the Representation have a Standard
    Representation updatedRepresentation=testUtil.createRepresentation();
    updatedRepresentation.setRepresentationStandard(URI.create("https://representation/standard"));
    updatedRepresentation.setInstance(List.of(testUtil.createArtefact()));
    producerResource.setRepresentation(List.of(updatedRepresentation));

    // Update the Resource to add a standardLicense
    producerResource.setStandardLicense(URI.create("https://standard/license"));

    brokerService.addResourceToFuseki(producerResource, connectorProducer.getId());

    // Execute test
    ResultSet result= jenaService.select(QueryUtil.constructQuery(sparqlQuery));
    ByteArrayOutputStream outputStream=converSelectOutputToTSV(result);

    // Creates a list using the \n as the separator for the rows
    ArrayList<String> resultAsList = new ArrayList<>(Arrays.asList(outputStream.toString().split("\n")));
    resultAsList.forEach(System.out::println);


    // It returns 2 rows + header. However, both rows are the same, so 3. If using the brokerService, it automatically adds a DISTINCT in the select and garantees that only 2 results are returned
    assertEquals(resultAsList.size(), 3);

  }

  @Test
  void addModel() {
    Connector connector=testUtil.createConnector();
    Model model= ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, IOUtils.toInputStream(connector.toString()), Lang.JSONLD);
    String connectorUniqueID = GraphNameUtil.getConnectorUniqueIDFromConnectorURL(connector.getId());
    String uuid=modelService.replaceSubject(model, "connector", connectorUniqueID);

    testUtil.removeAllGraphsFromDB(accessor);

    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 0);
    jenaService.appendModel(FusekiUtil.getBaseGraphName(accessor,connectorUniqueID +SEPARATOR+uuid), model);
    assertEquals(modelService.listAllGraphsExceptAdmin().size(), 1);
  }

  private ByteArrayOutputStream converSelectOutputToTSV(ResultSet result){
    ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
    // Convert the ResultSet to TSV
    ResultSetFormatter.outputAsTSV(outputStream, result);
    return outputStream;
  }
}