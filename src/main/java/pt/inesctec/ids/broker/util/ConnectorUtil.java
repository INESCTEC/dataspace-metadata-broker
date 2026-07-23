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
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.BadRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConnectorUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorUtil.class);

  public static String serialize(Connector connector) {
    Serializer serializer = new Serializer();
    FusekiConverter converter=new FusekiConverter();

    LOGGER.debug("Serialize Connector...");

    try {
      String json= serializer.serialize(connector);
      System.out.println("serialized: \n"+json);
      LOGGER.debug("... Connector serialized");
      return json;
    }
    catch (IOException e) {
      LOGGER.error("Error while serializing Connector...");
      LOGGER.error("... "+e.getMessage());
      throw new BadRequestException(e.getMessage());
    }
  }

  public static Connector deserialize(Model model) {
    Serializer serializer = new Serializer();
    FusekiConverter converter=new FusekiConverter();

    LOGGER.debug("Deserialize Connector...");

    try {
      Connector json= serializer.deserialize(converter.modelToString(model), Connector.class);
      LOGGER.debug("... Connector deserialized");
      return json;
    }
    catch (IOException e) {
      LOGGER.error("Error while deserialize Connector...");
      LOGGER.error("... "+e.getMessage());
      throw new BadRequestException(e.getMessage());
    }
  }

  public static Connector deserialize(InputStream inputStream)  {
    try {
      return deserialize(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      LOGGER.error("Could not convert inputStream to String...");
      LOGGER.error("... "+e.toString());
      throw new BadRequestException(e.getMessage());    }
  }

  public static Connector deserialize(String string) {
    Serializer serializer = new Serializer();
    Connector json;

    LOGGER.debug("Deserializing Connector...");

    try {
      json= serializer.deserialize(string, Connector.class);
      LOGGER.debug("... Connector deserialized");
      return json;
    }
    catch (IOException e) {
      LOGGER.error("Could not deserialize Connector...");
      LOGGER.error("... "+e.getMessage());
      throw new BadRequestException(e.getMessage());
    }
  }


  /**
   * Created the Connector Model
   * */
  public static Model createModel( Connector r) {
    InputStream in = IOUtils.toInputStream(r.toRdf(), "UTF-8");
    Model model=ModelFactory.createDefaultModel().read(in, null, RDFLanguages.strLangJSONLD);

    return model;
  }

  /**
   *  Add SameAs to the Connector and Catalog
   * */
  public static void addSameAsToConnector(Connector connector, Model model){
    LOGGER.debug("...Adding SameAs to the Connector...");
    // Add SameAs to the Connector
    FusekiUtil.addSameAs(model, String.valueOf(connector.getId()));

    // Add SameAs to the Catalogs, PublicKey and ConnectorEndpoint
    getCompleteConnectorModel(model, connector).forEach(child ->{
      FusekiUtil.addSameAs(model, String.valueOf(child.getSubject()));
    });
  }


  public static List<Statement> getConnectorStatementsFromModel(Model model, String graphName, String connectorID){

    // This list has all statements that have the ConnectorID in the subject and that have a URI on the Object.
    // This way will work if the Information Model is updated
    List<String> possibleChildren=new ArrayList<>();

    // Add all statements where the GraphName is the Subject.
    List<Statement> statementsOnDB=model.listStatements(ResourceFactory.createResource(graphName), null, (RDFNode) null).toList();

    statementsOnDB.forEach(statement -> {
      if(statement.getObject().isResource() &&
              !statement.getObject().equals(ResourceFactory.createResource(connectorID)) ){  // Need to remove the Connector SameAs, since it created a loop
        possibleChildren.add(String.valueOf(statement.getObject()));
      }
    });

    possibleChildren.forEach(child -> {
      // If any of the possibleChildren is the subject on the model, add it to the 'statementsOnDB'
      model.listStatements(ResourceFactory.createResource(child), null, (RDFNode) null).forEach(statementsOnDB::add);
    });

    //statementsOnDB.forEach(System.out::println);


    return statementsOnDB;
  }

  private static List<Statement> getConnectorModelChildren(Model model, Connector connector){
    // This list has all statements that have the ConnectorID in the subject and that have a URI on the Object.
    // This way will work if the Information Model is updated
    List<String> possibleChildren=new ArrayList<>();

    // Add all statements where the Connector ID is the Subject.
    List<Statement> children=new ArrayList<>();


    // The subject is the ConnectorID because this is run before the Sujects Rewrite
    model.listStatements(
            ResourceFactory.createResource(connector.getId().toString()),
            null,
            (RDFNode) null)
            .forEach(statement -> {
              if(statement.getObject().isResource() &&
                !statement.getObject().equals(ResourceFactory.createResource(connector.getId().toString())) ){  // Need to remove the Connector SameAs, since it created a loop
          possibleChildren.add(String.valueOf(statement.getObject()));
      }
    });

    possibleChildren.forEach(child -> {
      // If any of the possibleChildren is the subject on the model, add it to the 'statementsOnDB'
      model.listStatements(ResourceFactory.createResource(child), null, (RDFNode) null).forEach(children::add);
    });

    //statementsOnDB.forEach(System.out::println);


    return children;
  }

  private static List<Statement> getCompleteConnectorModel(Model model, Connector connector){
    // This list has all statements that have the ConnectorID in the subject and that have a URI on the Object.
    // This way will work if the Information Model is updated
    List<String> possibleChildren=new ArrayList<>();

    // Add all statements where the Connector ID is the Subject.
    List<Statement> children=new ArrayList<>();


    // The subject is the ConnectorID because this is run before the Sujects Rewrite
    model.listStatements(
            null,
            null,
            (RDFNode) null)
        .forEach(statement -> {
          if(statement.getObject().isResource() &&
              !statement.getObject().equals(ResourceFactory.createResource(connector.getId().toString())) ){  // Need to remove the Connector SameAs, since it created a loop
            possibleChildren.add(String.valueOf(statement.getObject()));
          }
        });

    possibleChildren.forEach(child -> {
      // If any of the possibleChildren is the subject on the model, add it to the 'statementsOnDB'
      model.listStatements(ResourceFactory.createResource(child), null, (RDFNode) null).forEach(children::add);
    });

    //statementsOnDB.forEach(System.out::println);


    return children;
  }

  public static Model rewriteSubjects(Connector connector, Model connectorModel, String baseName){

    LOGGER.debug("Rewritting Connectors subjects...");
    List<Statement> newStatements=new ArrayList<>();
    List<Statement> oldStatements=new ArrayList<>();
    Map<String, String> map=new HashMap<>();

    // Handles the BaseConnector on the Subject
    connectorModel.listStatements(ResourceFactory.createResource(connector.getId().toString()), null, (RDFNode) null)
            .forEach(statement ->{
              oldStatements.add(statement);
              newStatements.add(FusekiUtil.addStatement(baseName, statement.getPredicate(), statement.getObject()));
              map.put(connector.getId().toString(), baseName);
    });

    List<Statement> childrenStatements=ConnectorUtil.getCompleteConnectorModel(connectorModel, connector);

    // Handles the Connector Children (i.e., Public Key, Catalog and ConnectorEndpoint; or, Resource if it exists)
    childrenStatements.forEach(statement ->{
      String baseUrlChild=FusekiUtil.buildNewSubjectName(baseName, URI.create(statement.getSubject().toString()));
      oldStatements.add(statement);
      newStatements.add(FusekiUtil.addStatement(baseUrlChild, statement.getPredicate(), statement.getObject()));
      map.put(statement.getSubject().toString(), baseUrlChild);
    });

    connectorModel.remove(oldStatements);
    connectorModel.add(newStatements);

    /*System.out.println("OLD Statements");
    oldStatements.forEach(System.out::println);

    System.out.println("New Statements");
    newStatements.forEach(System.out::println);*/

    oldStatements.clear();
    newStatements.clear();


    // On the BaseConnector Model, update the object of each child (i.e., replace the ID with the new subject)
    map.forEach((v,k)->{
      connectorModel.listStatements()
              .filterDrop(i-> i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
              .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(v)))
              .forEach(j->{
                oldStatements.add(j);
                newStatements.add(ResourceFactory.createStatement(
                        ResourceFactory.createResource(String.valueOf(j.getSubject())),
                        j.getPredicate(),
                        ResourceFactory.createResource(k)));
              });
    });
    connectorModel.remove(oldStatements);
    connectorModel.add(newStatements);

    LOGGER.debug("... Subjects rewritten");
    return connectorModel;
  }

  /**
   * This method returns the Statement that connects the Connector internal ID with the Connector ID
   *
   * @param model model to search the resource
   * @param objectID ID to search for
   * @return optional of a statement
   * */
  public static Optional<Statement> getConnectorSameAsStatementFromDB(ModelDto model, URI objectID){
    return model.getModel().listStatements()
            .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
            .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(objectID.toString()))).nextOptional();
    // Uses the SameAs
  }

  /**
   * This method gets the model and its children (After Subject Rewriting)
   *
   * It is mainly used to reconstruct an Object (e.g., Connector) automatically
   * @return a model with all the statements that belong to an ID (resource and its children (e.g., representation, artefact, ...)
   * */
  public static Model getConnectorModelFromDB(URI graphName, ModelDto model){

    LOGGER.debug("Getting the " + graphName +" from the DB");

    Optional<Statement> connectorStatement=model.getModel().listStatements()
            .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
            .filterKeep(i->i.getSubject().equals(ResourceFactory.createResource(graphName.toString()))).nextOptional();

    List<Statement> statementsToReturn = getConnectorStatementsFromModel(
            model.getModel(),
            graphName.toString(),
            connectorStatement.get().getObject().toString());


    return ModelFactory.createDefaultModel().add(statementsToReturn);

  }

  public static Model getConnectorModelFromDB_WithoutSubModels(URI graphName, ModelDto model){

    LOGGER.debug("Getting the " + graphName +" from the DB");

    List<Statement> connectorStatement=model.getModel().listStatements()
            .filterKeep(i->i.getSubject().equals(ResourceFactory.createResource(graphName.toString()))).toList();

    FusekiConverter converter=new FusekiConverter();
    System.out.println(converter.modelToTurleFormat(converter.listOfStatements2Model(connectorStatement)));
    //connectorStatement.forEach();


    return ModelFactory.createDefaultModel().add(connectorStatement);

  }
}
