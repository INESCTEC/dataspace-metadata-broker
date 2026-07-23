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

import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceCatalog;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

import static pt.inesctec.ids.broker.config.FusekiConstants.SEPARATOR;
import static pt.inesctec.ids.broker.config.IDSConstants.core;

public class ResourceUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtil.class);

  public static Resource deserialize(Model model) {
    Serializer serializer = new Serializer();
    FusekiConverter converter=new FusekiConverter();

    LOGGER.debug("Deserialize Resource...");

    try {
      Resource json= serializer.deserialize(converter.modelToString(model), Resource.class);
      LOGGER.debug("... Resource deserialized");
      return json;
    }
    catch (IOException e) {
      LOGGER.error("Error while deserialize Resource...");
      LOGGER.error("... "+e.getMessage());
      throw new BadRequestException(e.getMessage());
    }
  }

  public static Resource deserialize(InputStream inputStream) {
    Serializer serializer = new Serializer();
    Resource json;

    LOGGER.debug("Deserialize Resource...");
    try {
      json= serializer.deserialize(IOUtils.toString(inputStream), Resource.class);
      LOGGER.debug("... Resource deserialized");
      return json;
    }
    catch (IOException e) {
      LOGGER.error("Error while deserialize Resource...");
      LOGGER.error("... "+e.getMessage());
      throw new BadRequestException(e.getMessage());
    }
  }

  /**
   * Creates the Resource Catalog Model
   * */
  public static Model createResourceCatalogModel( ResourceCatalog catalog) {
    InputStream in = IOUtils.toInputStream(catalog.toRdf(), "UTF-8");
    Model model=ModelFactory.createDefaultModel().read(in, null, RDFLanguages.strLangJSONLD);

    return model;
  }

  /**
   * Creates the Resource Model
   * */
  public static Model createModel(Resource r) {
    InputStream in = IOUtils.toInputStream(r.toRdf(), "UTF-8");
    Model model=ModelFactory.createDefaultModel().read(in, null, RDFLanguages.strLangJSONLD);

    return model;
  }

  /**
   * Adds the offer to the Resource Catalog
   *
   * @param model Model
   * @param offer the offer id (i.e., IDS Resource ID) (e.g.,
   */
  public static void addOffertToCatalog(ModelDto model, URI offer) {
    LOGGER.debug("Adding Offer to the Catalogue");

    // This is always returning only one value.
    String offerInternalID=model.mapInternalIDsWithIDs(offer).get(0).getSubject().toString();

    // Get the connector statement where the Catalog is defined
    List<Statement> catalogListOnConnector=model.getModel().listStatements(ResourceFactory.createResource(String.valueOf(model.getName())),
            ResourceFactory.createProperty(core+"resourceCatalog"), (org.apache.jena.rdf.model.Resource) null).toList();

    // Add the offer to the ids:ResourceCatalog.
    model.getModel().add(ResourceFactory.createStatement( //Add a new triple to the model
            ResourceFactory.createResource(catalogListOnConnector.stream().findAny().get().getObject().toString()), //Subject: The Catalog Subject
            ResourceFactory.createProperty(core+"offeredResource"), //Predicate:  ids:offeredResource
            ResourceFactory.createResource(String.valueOf(offerInternalID)))); //Object: The original URI*/

  }

  /**
   * Adds the offer to the Resource Catalog
   * @param catalogs list of Catalogs where the resource will be inserted
   * @return ca
   * */
  public static Statement chooseCatalog(List<Statement> catalogs) {
    LOGGER.debug("Selectiong a Catalogue");

   // If a Connector does not have a Catalogue was already handled on the main method

    // If a Connector only has a Catalog
    if (catalogs.size()==1){
      // Get the connector statement where the Catalog is defined
      return catalogs.stream().findFirst().get();
    }
    // If a Connector has multiple Catalogs (catalogs.size()>1)
    else {
      // TODO: Currently is using any catalog. The IDS Information Model does not yet support finding the catalog using the offer
      return catalogs.stream().findAny().get();
    }
  }


  /**
   *  Check that the Resource has a contract, rule, representation and artefact.
   *  Without these values, a Resource cannot be inserted on the Broker
   * */
  public static void checkDataModel(Resource resource){
    // Check if the representation is present.
    if(resource.getRepresentation() ==null || resource.getRepresentation().isEmpty()){
      throw new InvalidValueException("It needs to have a Representation.");
    }

    // Check if the artefact is present.
    resource.getRepresentation().forEach(representation -> {
      if(representation.getInstance().isEmpty() || representation.getInstance()==null){
        throw new InvalidValueException("It needs to have a Artefact.");
      }
    });

    // Check if the contract is present.
    if(resource.getContractOffer() ==null || resource.getContractOffer().isEmpty()){
      throw new InvalidValueException("It needs to have a Contract.");
    }

    // Check if the rule is present.
    resource.getContractOffer().forEach(contractOffer -> {
      if(contractOffer.getPermission().isEmpty() && contractOffer.getObligation().isEmpty() && contractOffer.getProhibition().isEmpty()){
        throw new InvalidValueException("It needs to have a Rule.");
      }
    });
  }

  /**
   *  Add SameAs to the Resource, contract, rule, representation and artefact.
   * */
  public static void addSameAsToResource(Resource resource, Model model){
    // Add SameAs to the Resource
    FusekiUtil.addSameAs(model, String.valueOf(resource.getId()));

    // Add SameAs to the Representations and the Artefacts
    resource.getRepresentation().forEach(representation -> {
      FusekiUtil.addSameAs(model, String.valueOf(representation.getId()));
      representation.getInstance().forEach(artefact -> FusekiUtil.addSameAs(model, String.valueOf(artefact.getId())));
    });


    // Add SameAs to the Contracts and the Rules
    resource.getContractOffer().forEach(contractOffer -> {
      FusekiUtil.addSameAs(model, String.valueOf(contractOffer.getId()));
      contractOffer.getObligation().forEach(rule -> FusekiUtil.addSameAs(model, String.valueOf(rule.getId())));
      contractOffer.getPermission().forEach(rule -> FusekiUtil.addSameAs(model, String.valueOf(rule.getId())));
      contractOffer.getProhibition().forEach(rule -> FusekiUtil.addSameAs(model, String.valueOf(rule.getId())));
    });
  }



  public static Model rewriteSubjects(Resource resource, ModelDto connectorModel, Model resourceModel, String catalogDatabaseURI){
    List<Statement> newStatements=new ArrayList<>();
    List<Statement> oldStatements=new ArrayList<>();
    Map<String, String> map=new HashMap<>();

    String baseURI=FusekiUtil.buildNewSubjectName(catalogDatabaseURI, resource.getId());

    // Handles the Offer on the Subject
    resourceModel.listStatements(ResourceFactory.createResource(resource.getId().toString()), null, (RDFNode) null).forEach(statement ->{
      oldStatements.add(statement);
      newStatements.add(FusekiUtil.addStatement(baseURI, statement.getPredicate(), statement.getObject()));
      map.put(resource.getId().toString(), baseURI);
    });

    if(resource.getRepresentation() !=null && !resource.getRepresentation().isEmpty()){
      resource.getRepresentation().forEach(representation -> {
        String baseUrlRepresentation=FusekiUtil.buildNewSubjectName(baseURI, representation.getId());

        resourceModel.listStatements(ResourceFactory.createResource(representation.getId().toString()), null, (RDFNode) null).forEach(statement ->{
          oldStatements.add(statement);
          newStatements.add(FusekiUtil.addStatement(baseUrlRepresentation
                  , statement.getPredicate(), statement.getObject()));
          map.put(representation.getId().toString(), baseUrlRepresentation);
        });

        // Take care of the Artefacts
        representation.getInstance().forEach(artefact->{
          resourceModel.listStatements(ResourceFactory.createResource(artefact.getId().toString()), null, (RDFNode) null).forEach(statement ->{
            oldStatements.add(statement);
            newStatements.add(FusekiUtil.addStatement(
                FusekiUtil.buildNewSubjectName(baseUrlRepresentation, artefact.getId()), statement.getPredicate(), statement.getObject()));
            map.put(artefact.getId().toString(), baseUrlRepresentation+SEPARATOR+artefact.getId().hashCode());
          });
        });
      });
    }

    if(resource.getContractOffer() !=null && !resource.getContractOffer().isEmpty()){
      resource.getContractOffer().forEach(contractOffer -> {
        String baseUrlcontractOffer=FusekiUtil.buildNewSubjectName(baseURI, contractOffer.getId());

        resourceModel.listStatements(ResourceFactory.createResource(contractOffer.getId().toString()), null, (RDFNode) null).forEach(statement ->{
          oldStatements.add(statement);
          newStatements.add(FusekiUtil.addStatement(baseUrlcontractOffer
                  , statement.getPredicate(), statement.getObject()));
          map.put(contractOffer.getId().toString(), baseUrlcontractOffer);
        });

        // Take care of the getProhibition
        contractOffer.getProhibition().forEach(rule->{
          resourceModel.listStatements(ResourceFactory.createResource(rule.getId().toString()), null, (RDFNode) null).forEach(statement ->{
            oldStatements.add(statement);
            newStatements.add(FusekiUtil.addStatement(
                FusekiUtil.buildNewSubjectName(baseUrlcontractOffer, rule.getId()),
                statement.getPredicate(), statement.getObject()));
            map.put(rule.getId().toString(), baseUrlcontractOffer+SEPARATOR+rule.getId().hashCode());
          });
        });
        // Take care of the getPermission
        contractOffer.getPermission().forEach(rule->{
          resourceModel.listStatements(ResourceFactory.createResource(rule.getId().toString()), null, (RDFNode) null).forEach(statement ->{
            oldStatements.add(statement);
            newStatements.add(FusekiUtil.addStatement(
                FusekiUtil.buildNewSubjectName(baseUrlcontractOffer, rule.getId()),
                statement.getPredicate(), statement.getObject()));
            map.put(rule.getId().toString(), baseUrlcontractOffer+SEPARATOR+rule.getId().hashCode());
          });
        });
        // Take care of the getObligation
        contractOffer.getObligation().forEach(rule->{
          resourceModel.listStatements(ResourceFactory.createResource(rule.getId().toString()), null, (RDFNode) null).forEach(statement ->{
            oldStatements.add(statement);
            newStatements.add(FusekiUtil.addStatement(
                FusekiUtil.buildNewSubjectName(baseUrlcontractOffer, rule.getId()),
                statement.getPredicate(), statement.getObject()));
            map.put(rule.getId().toString(), baseUrlcontractOffer+SEPARATOR+rule.getId().hashCode());
          });
        });
      });
    }

    resourceModel.remove(oldStatements);
    resourceModel.add(newStatements);

    oldStatements.clear();
    newStatements.clear();


    // On the Resource Model, update the object of each child (i.e., replace the ID with the new subject)
    map.forEach((v,k)->{
      resourceModel.listStatements()
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
    resourceModel.remove(oldStatements);
    resourceModel.add(newStatements);


    return resourceModel;
  }

  /**
   * This method returns the Statement that connects the Resource internal ID with the Resource ID
   *
   * @param model model to search the resource
   * @param resource resource to search for
   * @return optional of a statement
   * (e.g., [http://test-hostname:3032/connectors/connector-producer/-846787637/-435574692, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5]
   * */
  public static Optional<Statement> getResourceSameAsStatementFromDB(ModelDto model, Resource resource){
    return model.getModel().listStatements()
            .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
            .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(resource.getId().toString()))).nextOptional();
    // Uses the SameAs
  }

  /**
   * This method returns the Statement that connects the Artifact internal ID with the Artifact ID
   *
   * @param model model to search the resource
   * @param objectID ID to search for
   * @return optional of a statement
   * (e.g., [http://test-hostname:3032/connectors/connector-producer/-846787637/-435574692, http://www.w3.org/2002/07/owl#sameAs, https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5]
   * */
  public static Optional<Statement> getArtifactSameAsStatementFromDB(ModelDto model, URI objectID){
    return model.getModel().listStatements()
            .filterKeep(i->i.getPredicate().equals(ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs")))
            .filterKeep(i->i.getObject().equals(ResourceFactory.createResource(objectID.toString()))).nextOptional();
    // Uses the SameAs
  }
}
