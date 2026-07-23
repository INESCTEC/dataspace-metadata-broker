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
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.*;
import pt.inesctec.ids.broker.interfaces.IDSMessagingInterface;
import pt.inesctec.ids.broker.util.*;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static pt.inesctec.ids.broker.config.FusekiConstants.*;
import static pt.inesctec.ids.broker.config.IDSConstants.*;

@Service
public class BrokerService implements IDSMessagingInterface {

  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerService.class);

  private FusekiConverter fusekiConverter;

  private ModelService modelService;
  private AdminGraphService adminGraphService;
  private JenaService jenaService;

  private final FusekiConnectionAccessor accessor;


  public BrokerService(FusekiConnectionAccessor accessor) {
    this.fusekiConverter = new FusekiConverter();
    this.accessor = accessor;
    this.modelService=new ModelService(accessor);
    this.adminGraphService =new AdminGraphService(accessor);
    this.jenaService=new JenaService(accessor);
  }

  /**
   * Registers a Connector on Fuseki.
   *
   * There are 4 options:
   *  - The Connector was never registered;
   *  - The Connector is registered and disabled;
   *  - The Connector is registered and enabled. It suffers no alterations since it was published on the Broker;
   *  - The Connector is registered and anabled. However, it was updated.
   *
   * @return the Graph Complete URI (e.g., http://localhost:3030/connectors/localconnector/280850432)
   * */
  @Transactional
  public String addConnectorToFuseki(Connector receivedPayload){
    LOGGER.info("Registering the Connector...");

    String connectorUniqueID= GraphNameUtil.getConnectorUniqueIDFromConnectorURL(receivedPayload.getId());
    String hash= String.valueOf(receivedPayload.getId().hashCode());
    String graphName=FusekiUtil.buildGraphName(accessor, connectorUniqueID+SEPARATOR+hash);

    // Checks if exists a graph with this ConnectorID.
    Optional<String> optionalCompleteGraphName=modelService.convertConnectorIDToGraphName(receivedPayload.getId());


    // Create a model using the received Connector
    Model receivedConnectorModel=ConnectorUtil.createModel(receivedPayload);

    // Add SameAs
    ConnectorUtil.addSameAsToConnector(receivedPayload, receivedConnectorModel);

    // Rewrite Subjects
    ConnectorUtil.rewriteSubjects(receivedPayload, receivedConnectorModel, graphName);

    /*If it does not exist a Connector Graph that has this ConnectorID, it means that the Connector was never registered
        Registering Connector
     */
    if(!optionalCompleteGraphName.isPresent()){
      LOGGER.debug("... Connector does not exist...");

      // If the Connector is not registered, registered it and activate it
      ModelDto modelDto= jenaService.appendModel(graphName, receivedConnectorModel);

      // Add the Connector to the Admin Graph
      adminGraphService.addConnector(modelDto.getName());

      return modelDto.getName().toString();
    }


    /*
    * Handles the behaviour if the Connector is already registered on the Broker
    * */
    LOGGER.info("...Connector is already registered...");
    /*
     * The Connector was updated!
     * Handles the updates
     * */

    // Add all statements focused on the Connector
    LOGGER.info("...Adding all statements focused on the Connector...");
    ModelDto modelOnDB=jenaService.getModel(optionalCompleteGraphName.get());

    List<Statement> statementsOnDB=ConnectorUtil.getConnectorStatementsFromModel(modelOnDB.getModel(), optionalCompleteGraphName.get(), receivedPayload.getId().toString());
    Model connectorModelOnDB=ModelFactory.createDefaultModel().add(statementsOnDB);

    // Checks if the Connector is active
    if(adminGraphService.checkIfGraphIsActive(optionalCompleteGraphName.get())){
      /*
       * Check if the message brings any updates (e.g., a new catalog).
       * If the Models are different, means that the Connector had changes.
       * */
      if(FusekiUtil.getStatementsDiff(receivedConnectorModel, connectorModelOnDB).hasDiff()){
        LOGGER.debug("...Connector statement differences...");

        // Remove old and insert new
        FusekiUtil.removeModel(modelOnDB, connectorModelOnDB);
        FusekiUtil.joinModels(modelOnDB.getModel(), receivedConnectorModel);

        // If the difference is on the catalog, add the offer
        try {
          modelOnDB=updateConnector_checkIfTheDifferenceIsOnTheCatalog(statementsOnDB, receivedPayload, receivedConnectorModel, modelOnDB);
        }catch (InvalidValueException exception){
          // It means that the new connector does not hava a Catalog, but it has an offer
          throw new BadRequestException(exception.getMessage());
        }

        jenaService.updateModel(modelOnDB);
        return optionalCompleteGraphName.get();
      }
      else {
        LOGGER.debug("...There are no alterations.");
        return optionalCompleteGraphName.get();
      }
    }
    // If is not active, activate it
    else {
      // Activate graph
      adminGraphService.activateConnector(optionalCompleteGraphName.get());

      // Check if the Connector was updated
      if(FusekiUtil.getStatementsDiff(receivedConnectorModel, connectorModelOnDB).hasDiff()){
        // Remove old and insert new
        FusekiUtil.removeModel(modelOnDB, connectorModelOnDB);
        FusekiUtil.joinModels(modelOnDB.getModel(), receivedConnectorModel);

        // If the difference is on the catalog, add the offer
        try {
          modelOnDB=updateConnector_checkIfTheDifferenceIsOnTheCatalog(statementsOnDB, receivedPayload, receivedConnectorModel, modelOnDB);
        }catch (InvalidValueException exception){
          // It means that the new connector does not hava a Catalog, but it has an offer
          throw new BadRequestException(exception.getMessage());
        }

        jenaService.updateModel(modelOnDB);
      }
      return optionalCompleteGraphName.get();
    }
  }

  /**
   * Sees if, when adding a connector, the difference between the connector on Fuseki and the Connector sent is on the Catalogue model, where it points to the Resource Offer
   * @return udated modelOnDB, with the offer (e.g.,<https://test-broker.com/id/test/catalog/ksjbxdiqusd> ids:offeredResource  <https://test-broker.com/id/test/offer/ksjbxdiqusd> .)
   * */
  private ModelDto updateConnector_checkIfTheDifferenceIsOnTheCatalog(List<Statement> statementsOnDB, Connector receivedPayload,
                                                                      Model receivedConnectorModel, ModelDto modelOnDB) {
    LOGGER.debug("Checking if the Connector has a Catalog ...");
    // Get list of Statements where the predicate is 'https://w3id.org/idsa/core/offeredResource'. The object will be the rewritten link
    List<Statement> offeredResorces=statementsOnDB.stream()
            .filter(statement -> statement.getPredicate().equals(ResourceFactory.createResource(core+"offeredResource")))
            .toList();

    // If there are no offered resources, the difference is somewhere else and the method returns. Otherwise, add the offers to the modelOnDB
    if(!offeredResorces.isEmpty()){
      LOGGER.debug("...Connector on Database has catalogs...");
      // IF the Connector does not have a Catalog
      if(receivedConnectorModel
              .listStatements(ResourceFactory.createResource(modelOnDB.getName().toString()), ResourceFactory.createProperty(core+"resourceCatalog"), (RDFNode) null)
              .toList().isEmpty()){
        LOGGER.debug("...There are no catalogs.");
        throw new InvalidValueException("Catalog of connector "+receivedPayload.getId()+" not found. Did you send a ConnectorUpdateMessage yet?");
      }
      else { //IF the Connector has a Catalog
        // Create a list of catalogs
        LOGGER.debug("...There are catalogs.");
        AtomicReference<String> baseURLCatalog = new AtomicReference<>();
        List<Statement> connectorCatalogsList=new ArrayList<>();
        receivedPayload.getResourceCatalog().forEach(catalog -> {
          baseURLCatalog.set(FusekiUtil.buildNewSubjectName(modelOnDB.getName().toString(), catalog.getId()));
          receivedConnectorModel.listStatements(ResourceFactory.createResource(baseURLCatalog.get()), null, (RDFNode) null).forEach(connectorCatalogsList::add);
        });
        // For each offer, add it to the new catalog
        offeredResorces.forEach(resource ->{
          LOGGER.debug("Resource: "+resource);
          // I need to find this value because this way the next method can be reused when adding the offer to the Catalog.
          // On the method, I'll find the original value (resource.getObject()) again
          String offerIDSID= String.valueOf(
                  modelOnDB.mapIDsWithInternalIDs(URI.create(resource.getObject().toString()))
                          .stream().findFirst().get().getObject());

          ResourceUtil.addOffertToCatalog(modelOnDB, URI.create(offerIDSID));
        });

      }
    }
    return modelOnDB;

  }



  public String executeSPARQLQuery(String receivedPayload) {
     /* I can do this because THE SELECT and the CONSTRUCT queries (the only using returnType)
     * have the default case, which makes invalid return types return as the default type */
    return this.executeSPARQLQuery(receivedPayload, "defaultValue");
  }

  public String executeSPARQLQuery(String receivedPayload, String returnType) {
    JenaService jenaService =new JenaService(accessor);

    /*LOGGER.debug("Original Query: ");
    LOGGER.debug(receivedPayload.replaceAll("\n", " "));*/

    // Query used to determine the type
    Query originalQuery;

    // Query used for the SELECT
    try {
      originalQuery = QueryUtil.constructQuery(receivedPayload);
    }
    catch (BadRequestException e) {
      LOGGER.error("Error parsing query: ");
      LOGGER.error(e.getMessage());

      // Specify the DELETE. Otherwise, the message is a generic error message
      if(receivedPayload.toUpperCase().contains("DELETE")){
        throw new BadRequestException("Query type is not valid. Valid values are: ASK, SELECT, CONSTRUCT and DESCRIBE");
      }
      throw new BadRequestException(e.getMessage());
    }

    // Handles if Query is SELECT. By default, it returns an OutputStream in TSV format. However, 4 formats are acceptable
    if (originalQuery.isSelectType()){
      return handleSelect(receivedPayload, accessor, jenaService, returnType);
    }

    //Handles if Query is DESCRIBE. Returns a Model in Turtle Format
    if(originalQuery.isDescribeType())
    {
      return handleDescribe(receivedPayload, accessor, jenaService);
    }

    //Handles if Query is CONSTRUCT. By default, returns an RDF Graph.
    if(originalQuery.isConstructType())
    {
      return handleConstruct(receivedPayload, jenaService, returnType);
    }

    //ASK returns a boolean value
    if(originalQuery.isAskType())
    {
      return handleAsk(receivedPayload, jenaService);
    }
    //Other query types are not supported. This should never happen, since the constructQuery handles this
    throw new BadRequestException("Query type is not valid. Valid values are: ASK, SELECT, CONSTRUCT and DESCRIBE");
  }

  private String handleDescribe(String receivedPayload, FusekiConnectionAccessor accessor, JenaService jenaService) {
    LOGGER.debug("Query is a DESCRIBE...");

    Query constructedQuery = QueryUtil.rewriteConstructAndDescribe(receivedPayload, accessor);
    // If constructedQuery is null, it means that there are no active graphs
    if(constructedQuery==null){
      return new String();
    }

    Model resultModel= jenaService.describe(constructedQuery);
    StringWriter stringWriter = new StringWriter();
    RDFDataMgr.write(stringWriter, resultModel, RDFFormat.TURTLE_PRETTY);
    return stringWriter.toString();
  }

  private String handleSelect(String receivedPayload, FusekiConnectionAccessor accessor,
                                 JenaService jenaService, String returnType) {
    LOGGER.debug("Query is a SELECT...");
    Query constructedQuery = QueryUtil.rewriteSelect(receivedPayload, accessor);

    // If constructedQuery is null, it means that there are no active graphs
    if(constructedQuery==null){
      return new String(new ByteArrayOutputStream().toByteArray());
    }
    LOGGER.debug("Rewrited Query: ");
    LOGGER.debug(constructedQuery.toString().replaceAll("\n", " "));

    ResultSet results= jenaService.select(constructedQuery);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    switch (returnType) {
      case ResponseType.CSV -> ResultSetFormatter.outputAsCSV(outputStream, results);
      case ResponseType.TEXT -> ResultSetFormatter.out(outputStream,results);
      case ResponseType.JSON -> ResultSetFormatter.outputAsJSON(outputStream, results);
      default -> ResultSetFormatter.outputAsTSV(outputStream, results); // Default is TSV
    }

    return outputStream.toString();
    // Print the result to a table
    //ResultSetFormatter.out(System.out, results);
  }

  private String handleAsk(String receivedPayload, JenaService jenaService) {
    LOGGER.debug("Query is a ASK...");
    Query constructedQuery = QueryUtil.rewriteASK(receivedPayload, accessor);
    // If constructedQuery is null, it means that there are no active graphs
    if(constructedQuery==null){
      return new String();
    }
    return String.valueOf(jenaService.ask(constructedQuery));
  }

  private String handleConstruct(String receivedPayload, JenaService jenaService, String returnType) {
    LOGGER.debug("Query is a CONSTRUCT...");
    Query constructedQuery = QueryUtil.rewriteConstructAndDescribe(receivedPayload, accessor);

    // If constructedQuery is null, it means that there are no active graphs
    if(constructedQuery ==null){
      return new String();
    }

    Model resultModel= jenaService.construct(constructedQuery);
    StringWriter stringWriter = new StringWriter();
    switch (returnType){
      case ResponseType.JSON_LD -> RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD_COMPACT_PRETTY);
      case ResponseType.JSON -> RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD_COMPACT_PRETTY);
      case ResponseType.TURTLE -> RDFDataMgr.write(stringWriter, resultModel, RDFFormat.TURTLE_PRETTY);
      case ResponseType.TRIPLES -> RDFDataMgr.write(stringWriter, resultModel, RDFFormat.NTRIPLES);
      default -> RDFDataMgr.write(stringWriter, resultModel, RDFFormat.JSONLD_COMPACT_PRETTY); // JSON is the default
    }
    return stringWriter.toString();
  }


  /**
   * This method is called on the 'ResourceUpdateMessageHandler' to add a resource to the Connector
   * @param resource Resource
   * @param connectorID the Connector ID (e.g., https://test-broker.com/id/test/connector-producer)
   * @return the resource database URL
   * */
  public String addResourceToFuseki(de.fraunhofer.iais.eis.Resource resource, URI connectorID) {

    LOGGER.info("Registering the Resource...");

    // Find the connector to which the Resource belongs to on the DB (Currently it is a list)
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(connectorID);

    // See if the Connector exists
    if(!connectorModel.isPresent()){
      throw new ResourceNotFoundException("Connector", "ID", connectorID.toString());
    }

    // See if the Connector is active
    if(!adminGraphService.checkIfGraphIsActive(connectorModel.get().getName().toString())){
      // If is inactive, throws a message saying that is already active
      LOGGER.error("The Connector is inactive. Send a ConnectorUpdateMessage to activate it.");
      throw new ResourceNotFoundException("Connector", "ID", connectorID, "Did you send a ConnectorUpdateMessage yet?");
    }

    // Check if the Connector has a Catalog. If it does not, throw an error
    // Get list of Catalogues and see if the list is empty
    List<Statement> catalogs=new ArrayList<>();
    connectorModel.get().getModel().listStatements(null, null, ResourceFactory.createProperty(RESOURCE_CATALOG))
            .forEachRemaining(catalogs::add);

    if (catalogs.isEmpty()){
      throw new InvalidValueException("Catalog of connector "+connectorID+" not found. Did you send a ConnectorUpdateMessage yet?");
    }

    // Get a Catalog for the resource to be stored under
    Statement catalogWhereToInsertTheResource=ResourceUtil.chooseCatalog(catalogs);

    // Check to see if the Resource has contract, rule, representation and artefact. If it does not, throw an error
    try {
      ResourceUtil.checkDataModel(resource);
    }
    catch (InvalidValueException e){
      throw new InvalidValueException("Resource "+ resource.getId()+" cannot be inserted on the Broker. "+e.getMessage());
    }


    Model resourceModelToAdd=ResourceUtil.createModel(resource);

    // Add SameAs to the Resource, Contract, Rule, Representation and Artefact
    ResourceUtil.addSameAsToResource(resource, resourceModelToAdd);

    // Sees if the Offer already exists
    Optional<Statement> resourceIDOnDB_OptinalOfStatement=ResourceUtil
            .getResourceSameAsStatementFromDB(connectorModel.get(), resource);

    // If Resource already exists on DB
    if(resourceIDOnDB_OptinalOfStatement.isPresent()) {
      LOGGER.debug("Resource to be updated is already present");
      //Model resourceModelOnDB = ResourceUtil.getResourceModelFromDB(resource.getId(), connectorModel.get());
      Model resourceModelOnDB=
              modelService.getModelAndItsChildren(resourceIDOnDB_OptinalOfStatement.get(), connectorModel.get());

      // Check if no Artefact was removed. If it was, remove all outdated resources
      if(resourceModelToAdd.listStatements().toList().size() <
              resourceModelOnDB.listStatements().toList().size()){
        LOGGER.debug("Resource to be updated has less Artifacts than the one already stored on DB");
        LOGGER.debug("Resource on DB will be updated to remove the outdated Artifact");

        // Remove Old Resource from DB
        removeResourceFromFuseki_DBUpdate(connectorModel.get(), resourceModelOnDB);
      }
    }

    ResourceUtil.rewriteSubjects(resource, connectorModel.get(), resourceModelToAdd, catalogWhereToInsertTheResource.getSubject().toString());

    FusekiUtil.joinModels(connectorModel.get().getModel(), resourceModelToAdd);

    ResourceUtil.addOffertToCatalog(connectorModel.get(), resource.getId());

    // Add resource to BD
    jenaService.updateModel(connectorModel.get());

    String resourceInternalURI=connectorModel.get().mapInternalIDsWithIDs(resource.getId()).stream().findFirst().map(statement -> statement.getSubject().toString()).get();
    return resourceInternalURI;
  }

  /**
   * This method is called on the 'ResourceUnavailableMessageHandler' to remove a resource
   * @param resourceID Resource ID (e.g., https://test-broker.com/id/test/offer/dsfcjnkse)
   * @param connectorID the Connector ID (e.g., https://test-broker.com/id/test/connector-producer)
   * @return the model without the resource
   * */
  public Model removeResourceFromFuseki(URI connectorID, URI resourceID) {

    LOGGER.info("Removing the Resource...");

    // Find the connector to which the Resource belongs to on the DB (Currently it is a list)
    Optional<ModelDto> connectorModel=modelService.getModelWithConnectorIDEqualTo(connectorID);

    //System.out.println(connectorModel.get().getName());
    if(!connectorModel.isPresent()){
      throw new ResourceNotFoundException("Connector", "ID", connectorID.toString());
    }

    // See if the Connector is active
    if(!adminGraphService.checkIfGraphIsActive(connectorModel.get().getName().toString())){
      // If is inactive, throws a message saying that is already active
      LOGGER.error("The Connector is inactive. Send a ConnectorUpdateMessage to activate it.");
      throw new ResourceNotFoundException("Connector", "ID", connectorID, "Did you send a ConnectorUpdateMessage yet?");
    }

    // Sees if the Offer exists -> I construct the URL like this, because the DataspaceApp can create resources with the container name instead of the DNS.
    // And, when querying the broker using the Connector, which translates it to 'localhost', there is discripances on the resource name (e.g., producer-connector becomes localhost,
    // and the Broker could not locate the resource)
    Optional<Statement> resourceOptinal=
        connectorModel.get().getModel().listStatements(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"), (RDFNode) null)
        .filterKeep(i->
            // For the resources added using the DataspaceApp4EDI
            (
                i.getObject().toString().startsWith(resourceID.getScheme()+"://")
                    && i.getObject().toString().endsWith(resourceID.getPort()+resourceID.getPath())
            )
            // For the other resources (i.e., the object has the resourceID original value)
            ||
                i.getObject().toString().equals(resourceID.toString())).nextOptional();


    if(!resourceOptinal.isPresent()){
      // Throw error that resource does not exist
      LOGGER.error("Resource not found.");
      throw new ResourceNotFoundException("Resource", "ID", resourceID.toString(), "Did you send a ResourceUpdateMessage yet?");
    }
    LOGGER.debug("Resource exists...");

    Model modelToDelete=modelService.getModelAndItsChildren(resourceOptinal.get(), connectorModel.get());
    LOGGER.debug("Resource Model to delete...");

    removeResourceFromFuseki_DBUpdate(connectorModel.get(), modelToDelete);

    return connectorModel.get().getModel();
  }

  private synchronized void removeResourceFromFuseki_DBUpdate(ModelDto connectorModel, Model modelToDelete){
    LOGGER.debug("... Removing the Resource from the Model...");
    LOGGER.debug(fusekiConverter.modelToTurleFormat(modelToDelete).toString());

    FusekiUtil.removeModel(connectorModel, modelToDelete);
    LOGGER.debug("... Updating the Model...");
    jenaService.updateModel(connectorModel);

    LOGGER.debug("... Resource removed.");
  }


  /**
   * This method is called on the 'ConnectorUnavailableMessageHandler' to remove a connector
   * @param connectorID (e.g., https://test-broker.com/id/test/connector-producer)
   * */
  public void makeConnectorUnavailable(URI connectorID) {

    // Get Connector Graph Name
    Optional<String> connectorModel=modelService.convertConnectorIDToGraphName(connectorID);
    if(!connectorModel.isPresent()){
      throw new ResourceNotFoundException("Connector", "ID", connectorID, "Did you send a ConnectorUpdateMessage yet?");
      //throw new InvalidValueException("The Connector you are trying to remove ('"+connectorID+"') was not found. Did you send a ConnectorUpdateMessage yet?");
    }

    // Checks if the Connector is already inactive.
    if(!adminGraphService.checkIfGraphIsActive(connectorModel.get())){
      // If is inactive, throws a message saying that is already inactive
      LOGGER.error("The Connector is already unregistered");
      throw new InvalidValueException("The Connector you are trying to remove ('"+connectorID+"') was not found. Did you send a ConnectorUpdateMessage yet?");
    }
    else {
      adminGraphService.deactivateConnector(connectorModel.get().toString());
    }
  }

}
