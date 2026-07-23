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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.JenaService;

import java.io.UncheckedIOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static pt.inesctec.ids.broker.config.FusekiConstants.*;

public class FusekiUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(FusekiUtil.class);
  private static FusekiConverter fusekiConverter=new FusekiConverter();


  public static Model joinModels(Model existingModel, Model newModel) {
    LOGGER.debug("Joining Models");
    existingModel.add(newModel);
    LOGGER.debug("... Models Joined");
    return existingModel;
  }

  public static Model joinModels(Model existingModel, List<Statement> statementsToInsert) {
    LOGGER.debug("Joining Models");
    existingModel.add(fusekiConverter.listOfStatements2Model(statementsToInsert));
    LOGGER.debug("... Models Joined");
    return existingModel;
  }

  public static ModelDto removeModel(ModelDto dbModel, Model modelToDelete) {
    LOGGER.debug("Removing Model");
    dbModel.getModel().remove(modelToDelete);
    LOGGER.debug("... Model Removed");
    return dbModel;
  }

  public static ModelDto removeModel(ModelDto dbModel, List<Statement> statementsToDelete) {
    LOGGER.debug("Removing Model");
    dbModel.getModel().remove(statementsToDelete);
    LOGGER.debug("... Model Removed");
    return dbModel;
  }

  public static ModelDto addModel(ModelDto dbModel, List<Statement> statementsToAdd) {
    LOGGER.debug("Adding statements Model");
    dbModel.getModel().add(statementsToAdd);
    LOGGER.debug("... Model Added");
    return dbModel;
  }

  public static Model addSameAs(Model model, String id) {
    return model.add(ResourceFactory.createStatement( //Add a new triple to the model
            ResourceFactory.createResource(String.valueOf(id)), //Subject: The new URI
            ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"), //Predicate: owl:sameAs
            ResourceFactory.createResource(String.valueOf(id)))); //Object: The original URI
  }

  public static Statement addStatement(String resource, Property predicate, RDFNode object) {
    return ResourceFactory.createStatement( //Add a new triple to the model
            ResourceFactory.createResource(resource), //Subject
            predicate, //Predicate
            object); //Object
  }

  public static Map<String, String> addIDSPrefixs(){
    Map<String, String> prefixes=new HashMap<>();
    prefixes.put("ids", "https://w3id.org/idsa/core/");
    prefixes.put("idsc", "https://w3id.org/idsa/code/");
    prefixes.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns");
    return prefixes;
  }

  @NonNull
  public static StatementDiff getStatementsDiff(@NonNull final Model expectedModel, @NonNull final Model actualModel) {
    final StatementDiff diff = new StatementDiff();

    // Create list with expected statements
    final List<Statement> expectedStatements = new ArrayList<>();
    expectedModel.listStatements().forEachRemaining(expectedStatements::add);

    // Create list with actual statements
    final List<Statement> actualStatements = new ArrayList<>();
    actualModel.listStatements().forEachRemaining(actualStatements::add);

    // Find the missing statements
    expectedStatements
            .stream()
            .filter(expectedStatement -> actualStatements.stream().noneMatch(s -> CompareUtil.compareStatements(s, expectedStatement)))
            .forEach(expectedStatement -> diff.getMissingStatements().add(expectedStatement));

    // Find the excess statements
    actualStatements
            .stream()
            .filter(actualStatement -> expectedStatements.stream().noneMatch(s -> CompareUtil.compareStatements(s, actualStatement)))
            .forEach(actualStatement -> diff.getExcessStatements().add(actualStatement));

    return diff;
  }

  public static String buildNewSubjectName(String baseName, URI nameToEncode) {
    return baseName+SEPARATOR+nameToEncode.hashCode();
  }

  @Getter
  @NoArgsConstructor
  public static class StatementDiff {
    private final List<Statement> missingStatements = new ArrayList<>();
    private final List<Statement> excessStatements = new ArrayList<>();

    public boolean hasDiff() {
      return !missingStatements.isEmpty() || !excessStatements.isEmpty();
    }

    @Override
    public String toString() {
      if (this.hasDiff()) {
        return ">> missingStatements :: \n" + missingStatements.stream().map(Objects::toString).collect(Collectors.joining("\n")) + "\n\n" +
                ">> excessStatements :: \n" + excessStatements.stream().map(Objects::toString).collect(Collectors.joining("\n"));
      } else {
        return ">> No diff found";
      }
    }
  }

  public static String createConnectorGraphName(Connector connector){
    LOGGER.debug("Creating the Connector Graph Name...");
    return CONNECTORS+SEPARATOR+ GraphNameUtil.getConnectorUniqueIDFromConnectorURL(connector.getId())+SEPARATOR+connector.hashCode();
  }



  public static String getBaseGraphName(FusekiConnectionAccessor accessor, String graphName){
    return FusekiUtil.getCatalogURI(accessor) +"/"+graphName;
  }

  public static String getCatalogURI(FusekiConnectionAccessor accessor){
    return accessor.getCatalogUri();
  }

  /**
   * @param accessor
   * @param uniqueIdentifier the combination of connectorUniqueID+SEPARATOR+hash
   * @return complate graph name (e.g., http://localhost:3032/connectors/connector-producer/-1374631466)
   * */
  public static String buildGraphName(FusekiConnectionAccessor accessor, String uniqueIdentifier){
    return FusekiUtil.getCatalogURI(accessor)+uniqueIdentifier;
  }
}
