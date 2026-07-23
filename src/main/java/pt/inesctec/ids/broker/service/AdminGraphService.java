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

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.inesctec.ids.broker.builder.TriplesBuilder;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.dto.Triples;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.util.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static pt.inesctec.ids.broker.config.IDSConstants.*;

@Service
public class AdminGraphService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminGraphService.class);

  final
  FusekiConnectionAccessor accessor;

  private JenaService jenaService;
  private String adminGraphName;

  public AdminGraphService(FusekiConnectionAccessor accessor) {
    this.accessor = accessor;
    this.jenaService=new JenaService(accessor);
    this.adminGraphName=accessor.getAdminGraphName();
  }


  /**
   * This method adds a Connector to the Admin Graph
   * @param connectorGraphName the graph name (e.g., https://localhost:3031/connectors/connector-producer/-1311399301).
   * @return the new admin graph
   * */
  @Transactional
  public synchronized Model addConnector(URI connectorGraphName) {
    LOGGER.debug("Adding Connector to the Admin Graph...");
    LOGGER.debug("..."+ connectorGraphName);
    Triples graph = new TriplesBuilder()
            .subject(connectorGraphName)
            .predicate(GRAPH_IS_ACTIVE)
            .object(true)
            .build();

    // Create the Model
    Model model=addAdminModel(graph);

    // This appends the new model instead of replacing it
    jenaService.appendModel(adminGraphName, model);

    LOGGER.debug("... Graph Added");
    return model;
  }

  /**
   * This method checks if a Connector is active or not
   * @param graphName the graph name (e.g., https://localhost:3031/connectors/connector-producer/-1311399301).
   * @return if the graph is active or not
   * */
  @Transactional
  public boolean checkIfGraphIsActive(String graphName){
    //System.out.println("graphName: " +graphName);

    ModelDto model= getGraph();

    LOGGER.debug("Getting the triple for the Connector...");

    List<Statement> statements=model.listStatementsWithSubjectEqualTo(URI.create(graphName));
    // It will be empty if the Connector was never registred on the Admin Graph
    if(statements.isEmpty() || statements==null){
      LOGGER.info("There is no Connector with the graph name: ");
      LOGGER.info("... "+graphName);
      return false;
    }
    // Garantee that the Connector graph name is not repeated. Never should happen, because I'm garanteeing this before
    if(statements.size()>1){
      LOGGER.error("Connector graph name is already in use");
      throw new BadRequestException("Connector graph name is already in use");
    }

    LOGGER.debug("The Connector with the graph name "+graphName+" exists... ");
    if (statements.stream().findFirst().get().getObject().asLiteral().getBoolean()) {
      return true;
    }
    return false;
  }

  @Transactional
  public void initializeAdminGraph(){

    LOGGER.info("Checking if the Admin Graph exists...");

    ModelDto model= getGraph();

    // If the Graph does not exist. Should only happen once!!!
    if(model.getModel()==null){
      LOGGER.info("... It does not exist. One will be created ...");
      Triples graph = new TriplesBuilder()
              .subject(adminGraphName)
              .predicate(GRAPH_IS_ACTIVE)
              .object(false)
              .build();

      LOGGER.debug("... Getting Model from Triples...");
      Model newModel= ModelFactory.createDefaultModel();
      newModel.setNsPrefixes(FusekiUtil.addIDSPrefixs());
      newModel.add(graph.toGraphIsActiveStatement());

      // Save the model to Fuseki
      jenaService.appendModel(adminGraphName, newModel);
      LOGGER.debug("... Graph was added to Fuseki!");
    }
    else LOGGER.info("... It exists");
  }

  @Transactional
  public void deactivateConnector(String graphName){
    LOGGER.info("Getting the Admin Graph...");
    LOGGER.info("..."+graphName);
    // Get the Admin Graph
    ModelDto adminModel= getGraph();

    // The list should only have one value
    List<Statement> statements=adminModel.listStatementsWithSubjectEqualTo(URI.create(graphName));

    // Remove the old Statements from the Model
    FusekiUtil.removeModel(adminModel, statements);

    // Update the Statements
    LOGGER.info("Unregistering Connector from the Model: "+graphName);
    Statement oldS=statements.stream().findFirst().get();

    // Join the Statements to the Model
    //FusekiUtil.joinModels(adminModel.getModel(), fusekiConverter.listOfStatements2Model(statements));
    FusekiUtil.joinModels(adminModel.getModel(), List.of(new Triples(oldS.getSubject(), oldS.getPredicate(), false).toGraphIsActiveStatement()));

    // Update Model
    jenaService.updateModel(adminModel);

    LOGGER.info("... Connector Unregistered!!");
  }

  @Transactional
  public void activateConnector(String graphName){
    LOGGER.debug("Getting the Admin Graph...");
    LOGGER.debug("...Activating Connector: "+graphName);
    // Get the Admin Graph
    ModelDto adminModel= getGraph();

    // The list should only have one value
    List<Statement> statements=adminModel.listStatementsWithSubjectEqualTo(URI.create(graphName));


    // Remove the old Statements from the Model
    FusekiUtil.removeModel(adminModel, statements);

    // Update the Statements
    LOGGER.info("Registering Connector on the Model: "+graphName);
    if(!statements.stream().findFirst().isPresent()) {
      // This can happen if I remove the connector graph name from the admin graph, but don't remove its graph
      System.err.println("The Connector is not registered on admin Model, but it exists o Fusen the Database");
    }
    Statement oldS=statements.stream().findFirst().get();

    // Join the Statements to the Model
    FusekiUtil.joinModels(adminModel.getModel(), List.of(new Triples(oldS.getSubject(), oldS.getPredicate(), true).toGraphIsActiveStatement()));

    // Update Model
    jenaService.updateModel(adminModel);

    LOGGER.debug("... Connector Registered!!");
  }

  @Transactional
  public ModelDto getGraph() {
    LOGGER.debug("Getting Admin Graph...");
    try {
      // The exception is thrown if the graph does not exist.
      return jenaService.getModel(adminGraphName);

    }catch (HttpException e){
      return new ModelDto(adminGraphName, null);
    }
  }

  @Transactional
  public Model addAdminModel(Triples graph){
    LOGGER.debug("Getting Model from Triples...");
    Model model= ModelFactory.createDefaultModel();
    model.setNsPrefixes(FusekiUtil.addIDSPrefixs());
    model.add(graph.toGraphIsActiveStatement());
    return model;
  }

  public List<String> getListOfActiveConnectors(){
    LOGGER.debug("Getting List of Active Connectors...");

    List<String> activeGraphs=new ArrayList<>();

    // Get the AdminGraph
    ModelDto adminGraph=getGraph();


    // Get list of Active Connectors and then Get the Connector Graph name
    adminGraph.listStatementsWithObjectEqualTo(true).forEach(v->activeGraphs.add(v.getSubject().toString()) );

    return activeGraphs;
  }

}
