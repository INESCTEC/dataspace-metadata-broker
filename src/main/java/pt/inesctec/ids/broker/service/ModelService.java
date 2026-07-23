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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.util.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static pt.inesctec.ids.broker.config.FusekiConstants.*;

@Service
public class ModelService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelService.class);

  FusekiConverter fusekiConverter;

  final
  FusekiConnectionAccessor accessor;

  private String adminGraphName;

  private JenaService jenaService;

  public ModelService(FusekiConnectionAccessor accessor) {
    this.fusekiConverter = new FusekiConverter();
    this.accessor = accessor;
    adminGraphName=accessor.getAdminGraphName();
    jenaService=new JenaService(accessor);
  }


  /**
   *  Lists the name of all graphs stored on the DB
   * @return list of graph names
   * */
  public List<String> listAllGraphs() {
    List<String> graphs;

    Query select=QueryFactory.create("select distinct ?g {  graph ?g {?s ?p ?o} }");

    try {
      graphs=jenaService.select(select, "g");
    }
    catch (BadRequestException e) {
      throw new BadRequestException(e.getMessage());
    }
    return graphs;

  }


  /**
   *  Lists the name of all graphs stored on the DB, except the Admin Graph
   * @return list of graph names
   * */
  public List<String> listAllGraphsExceptAdmin() {
    List<String> graphs=listAllGraphs();
    graphs.remove(adminGraphName);
    return graphs;

  }


  /**
   *  Lists the Graphs Names using a Keyword
   * @return list of graph names containing a keyword
   * */
  public List<String> listGraphsUsingKeyword(String keyword){
    return this.listAllGraphsExceptAdmin().stream().filter(v-> v.contains(keyword)).collect(Collectors.toList());
  }

  /**
   *  Lists the Connectors (IDs) registred on the Broker
   *  Transforms a list of Connectors Graphs into a list of Connectors IDs
   * @return list of ConnectorIDs registerd on the Broker (e.g., https://test-broker.com/id/test/connector-producer and https://test-broker.com/id/test/connector-subcontracted)
   * */
  public List<String> getListOfConnectorsID() {
    List<String> graphs=listGraphsUsingKeyword(CONNECTORS);
    List<String> connectorIDs=new ArrayList<>();
    graphs.forEach(v->{
      //Model model=accessor.connect().fetch(v);
      Model model=jenaService.getModel(v).getModel();
      //model.write(System.out, "TURTLE");
      model.listStatements(ResourceFactory.createResource(v),
                      ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
                      (RDFNode) null)
              .forEachRemaining(j->connectorIDs.add(j.getObject().toString()));
    });

    return connectorIDs;
  }


  /**
   *  Gets the Connector Model Name using the Connector ID
   *  @param connector Connector ID (e.g., https://test-broker.com/id/test/connector-producer)
   * @return Optional of the Connector Model Name (e.g., http://localhost:3031/connectors/connector-producer/-1311399301)
   * */
  public Optional<String> convertConnectorIDToGraphName(URI connector) {
    List<String> graphs=listGraphsUsingKeyword(CONNECTORS);
    List<String> graphNameList=new ArrayList<>();
    graphs.forEach(v->{
      //Model model=accessor.connect().fetch(v);
      Model model=jenaService.getModel(v).getModel();
      model.listStatements(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
                      ResourceFactory.createResource(connector.toString()))
              .forEachRemaining(i->{
                graphNameList.add(v);
              });
    });

    return graphNameList.stream().findFirst();
  }

  /**
   *  Gets the ConnectorID using the Model Name
   *  @param graphName Connector Model Name (e.g., http://localhost:3031/connectors/connector-producer/-1311399301)
   * @return  Connector ID (e.g., https://test-broker.com/id/test/connector-producer)
   * */
  public String convertGraphName2ConnectorID(URI graphName){
    BrokerService BrokerService =new BrokerService(accessor);
    String getConnectorIDs="SELECT ?object\n" +
            "WHERE {\n" +
            "  graph <"+graphName+"> { \n" +
            "    {\n" +
            "      ?subject ?predicate ?object\n" +
            "      FILTER ( ( ?subject = <"+graphName+"> ) && ( ?predicate = <http://www.w3.org/2002/07/owl#sameAs> ) )\n" +
            "    }\n" +
            "  }\n" +
            "}";
    // Making it this way, makes the method handle all the graphs information and active connectors
    String result= BrokerService.executeSPARQLQuery(getConnectorIDs);

    // Converts the result into a list
    ArrayList<String> connectorID = new ArrayList<>(Arrays.asList(result.split("\n")));
    connectorID.remove("?object");

    if(!connectorID.stream().findFirst().isPresent()){
      throw new RuntimeException("Connector ID does not exist");
    }

    return connectorID.stream().findFirst().get().replaceAll("<", "").replaceAll(">", "");

  }

  /**
   *  Gets the Model that belongs to a Connector ID
   *  @param connector Connector ID (e.g., https://test-broker.com/id/test/connector-producer)
   * @return Optional of a Model DTO
   * */
  public Optional<ModelDto> getModelWithConnectorIDEqualTo(URI connector) {
    List<String> graphs=listGraphsUsingKeyword(CONNECTORS);
    List<ModelDto> models=new ArrayList<>();
    graphs.forEach(v->{
      //Model model=accessor.connect().fetch(v);
      Model model=jenaService.getModel(v).getModel();

      model.listStatements(null, ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
                      ResourceFactory.createResource(connector.toString()))
              .forEachRemaining(i->{
                  models.add(new ModelDto(URI.create(v), model));
              });
    });
    return models.stream().findFirst();
  }


  /**
   *  DELETEs all data from the DB
   *  (Except the Admin Graph)
   * */
  public void removeAllData() {
    removeConnectorGraphs();
  }


  /**
   *  DELETEs All Connector Graphs
   * */
  public void removeConnectorGraphs() {
    AdminGraphService adminGraphService=new AdminGraphService(accessor);
    // Removes the Connectors from the Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();

    // Removes Connector Graphs
    List<String> graphs= GraphNameUtil.urlList2UniqueIdentifierList(listGraphsUsingKeyword(CONNECTORS));

    graphs.forEach(v->{
      LOGGER.debug("Removing Data from Graph '"+ v+ "'");
      try { // I need to use the whole URL because I'm not using the Dataset name on the GraphName
        //accessor.connect().delete(FusekiUtil.buildGraphName(accessor, v));
        jenaService.removeGraph(FusekiUtil.buildGraphName(accessor, v));
        // Removes and Updates the Connectors from the Admin Graph
        FusekiUtil.removeModel(adminGraph, adminGraph.listStatementsWithSubjectEqualTo(URI.create(FusekiUtil.buildGraphName(accessor, v))));
        jenaService.updateModel(adminGraph);
        LOGGER.debug("...Data was removed successfully");
      }catch (Exception e){
        LOGGER.error("Error removing data from '"+v+"' graph: " + e.getMessage());
        System.err.println("Error removing data from '"+v+"' graph: " + e.getMessage());
      }
    });

  }

  /**
   *  DELETEs a specific Connector Graph
   * @param graphName (e.g., http://localhost:3031/connectors/connector-producer/-1487265002)
   * */
  public void removeConnectorGraph(String graphName) {

    AdminGraphService adminGraphService=new AdminGraphService(accessor);
    // Removes the Connectors from the Admin Graph
    ModelDto adminGraph=adminGraphService.getGraph();

    // If the model exists
    if(jenaService.getModel(graphName)!=null)
    {
      LOGGER.debug("Removing Data from Graph '"+graphName+ "'");
      try {
        jenaService.removeGraph(graphName);

        // Removes and Updates the Connectors from the Admin Graph
        FusekiUtil.removeModel(adminGraph, adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName)));
        jenaService.updateModel(adminGraph);
        LOGGER.debug("...Data was removed successfully");
      }catch (Exception e){
        LOGGER.error("Error removing data from '"+graphName+"' graph: " + e.getMessage());
        System.err.println("Error removing data from '"+graphName+"' graph: " + e.getMessage());
      }
    }
  }


  public String replaceSubject(Model model, String type, String id){
    // Generate a single UUID
    String uuid = UUID.randomUUID().toString();
    // Replace Subject with new one
    ResIterator subjects = model.listSubjects();
    while (subjects.hasNext()) {
      Resource oldSubject = subjects.nextResource();
      Resource newSubject = model.createResource("https://inesctec.pt/"+type+"/"+id+"/" + uuid);

      // Copy all the statements associated with the old subject to the new subject
      StmtIterator statements = oldSubject.listProperties();
      while (statements.hasNext()) {
        Statement stmt = statements.nextStatement();
        newSubject.addProperty(stmt.getPredicate(), stmt.getObject());
      }

      // Remove the old subject from the model
      model.removeAll(oldSubject, null, null);
    }
    return uuid;
  }

  /**
   * This method gets the model and the children
   * @param objectSameAsStatement Object SameAs Statement
   * @param model complete model
   * @return a model with all the statements (resource and its children (e.g., representation, artefact, ...)
   * Used instead of the other when the resourse uses the rewritten subject
   * */
  public Model getModelAndItsChildren(Statement objectSameAsStatement, ModelDto model){
    LOGGER.debug("Getting the Object from the DB");

    List<Statement> statementsToReturn=new ArrayList<>();
    List<String> possibleChildren=new ArrayList<>();

    // Add all Statements where the resourceID is the object (i.e., Catalog) to the list to remove
    model.getModel().listStatements(null, null, ResourceFactory.createResource(String.valueOf(objectSameAsStatement.getSubject())))
            .forEach(statementsToReturn::add);

    // Add all Statements where the resourceID is the subject (i.e. resource) to the list to remove, and all objects that have an URI to the list of possibleChildren
    model.getModel().listStatements(objectSameAsStatement.getSubject(),
            null, (RDFNode) null).forEach(v->{
              //System.out.println(v);
      statementsToReturn.add(v);

      if(v.getObject().isResource()){
        possibleChildren.add(v.getObject().toString());
        model.getModel().listStatements(ResourceFactory.createResource(String.valueOf(v.getObject())), null, (RDFNode) null).forEach(child->{

          if(child.getObject().isResource()){
            possibleChildren.add(String.valueOf(child.getObject()));
          }
        });
      }
    });

    // Add all Statements where the resourceID is the object (i.e., Catalog) to the list to remove
    model.getModel().listStatements(null, null, ResourceFactory.createResource(String.valueOf(objectSameAsStatement.getSubject())))
            .forEach(statementsToReturn::add);

    // For each possible child (e.g., Language or representation) see if there is a Subject with that child. If it does, remove it
    possibleChildren.forEach(child->{
      model.getModel().listStatements(ResourceFactory.createResource(child), null, (RDFNode) null).forEach(j ->{
        //System.out.println(j);
        statementsToReturn.add(j);
      });
    });
    //statementsToReturn.forEach(System.out::println);
    return ModelFactory.createDefaultModel().add(statementsToReturn);

  }

  /**
   * This method gets the model and its children
   *
   * It is mainly used to remove the model OR
   *    to reconstruct an Object (e.g., Connector) automatically
   * @return a model with all the statements that belong to an ID (resource and its children (e.g., representation, artefact, ...)
   * */
  public Model getModelAndItsChildren(URI objectID, ModelDto model){

    LOGGER.debug("Getting the " + objectID +" from the DB");

    List<Statement> statementsToReturn=new ArrayList<>();
    List<String> possibleChildren=new ArrayList<>();

    model.getModel().listStatements()
            .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
            .filterKeep(i->i.getObject().equals(ResourceFactory.createProperty(objectID.toString())))
            .forEach(statementsToReturn::add);

    if(statementsToReturn.isEmpty()) return ModelFactory.createDefaultModel();

    // Add all Statements where the subject with the object as the objectID is the subject (i.e. resource) to the list to return, and all objects that have an URI to the list of possibleChildren
    model.getModel().listStatements(statementsToReturn.get(0).getSubject(),
            null, (RDFNode) null).forEach(v->{
      statementsToReturn.add(v);

      if(v.getObject().isResource()){
        model.getModel().listStatements(ResourceFactory.createResource(String.valueOf(v.getObject())), null, (RDFNode) null).forEach(child->{
          statementsToReturn.add(child);

          if(child.getObject().isResource()){
            possibleChildren.add(String.valueOf(child.getObject()));
          }
        });
      }
    });

    // For each possible child (e.g., Language or representation) see if there is a Subject with that child. If it does, remove it
    possibleChildren.forEach(child->{
      model.getModel().listStatements(ResourceFactory.createResource(child), null, (RDFNode) null).forEach(j ->{
        //System.out.println(j);
        statementsToReturn.add(j);
      });
    });
    //statementsToRemove.forEach(System.out::println);
    return ModelFactory.createDefaultModel().add(statementsToReturn);

  }

  /**
   * This method gets a list with the self-description of all active connectors
   * @return a list with the self-description of all active connectors
   * */
  public List<Connector> getConnectorCatalog(){
    BrokerService brokerService =new BrokerService(accessor);

    List<Connector> activeConnectors = new ArrayList<>();

    String getConnectorIDs="SELECT ?subject ?__reserved\n" +
            "WHERE {graph ?__reserved {\n" +
            "  ?subject ?predicate ?object\n" +
            "  FILTER (?object = <https://w3id.org/idsa/core/BaseConnector> || ?object = <https://w3id.org/idsa/core/TrustedConnector>)\n" +
            "  }}";


    // Making it this way, makes the method handle all the graphs information and active connectors
    String result= brokerService.executeSPARQLQuery(getConnectorIDs);

    // Handles the convertion from TSV String into A List of graphs name and Connector ID
    List<Map<String, String>> connectorsAndGraphs= fusekiConverter.tsvTableString2List(result);

    connectorsAndGraphs.forEach(connector ->{
      // ?subject has the Connector ID
      String graphName=connector.get("?subject");
      // ?reserved has the Graph Name
      ModelDto fullModel =jenaService.getModel(connector.get("?reserved"));

      // Retrieves the Connector Model, as well as its children (e.g., Public Key and Catalogue)
      Model connectorModel=ConnectorUtil.getConnectorModelFromDB(URI.create(graphName), fullModel);
      //System.err.println(fusekiConverter.modelToTurleFormat(connectorModel));

      // IDs should be the Database IDs (i.e., internal ID) because when getting the description, the user can use various depths

      // Removes the sameAs to avoid a cycle when deserializing model
      connectorModel.removeAll(null,
              ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
                      null);

      Connector deserializedConnector=ConnectorUtil.deserialize(connectorModel);
      activeConnectors.add(deserializedConnector);
      //System.out.println(deserializedConnector.toRdf());

    });

    return activeConnectors;

  }

  /**
   *  Lists the Title of all resources registered on the graphname
   * @param graphName the graph to query
   * @return list of statements
   * */
  /*public List<Statement> listResourcesTitle(String graphName) {

    ResultSet resultSet=
        jenaService.select(QueryFactory.create("PREFIX ids: <https://w3id.org/idsa/core/>" +
            "SELECT  ?subject ?predicate ?object\n"+
            "WHERE { " +
            "graph <"+graphName+"> {" +

            "             ?subject a ids:Resource. " +
            "             ?subject ?predicate ?object " +
            "             FILTER (regex(str(?predicate), 'title','i')) \n" +
            "}}"));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ResultSetFormatter.out(System.out,resultSet);
    List<Statement> statementsList= new ArrayList<>();
    resultSet.forEachRemaining( statement -> statementsList.add(statement));
    // In case the resultSet do not return anything
    try{
      return resultSet.getResourceModel().listStatements().toList();
    } catch (NullPointerException exception){
      return new ArrayList<>();
    }
  }*/
}
