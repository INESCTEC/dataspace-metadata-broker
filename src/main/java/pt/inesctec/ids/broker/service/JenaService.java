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
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.interfaces.JenaServiceInterface;
import pt.inesctec.ids.broker.util.*;

import java.util.ArrayList;
import java.util.List;

import static pt.inesctec.ids.broker.config.IDSConstants.core;

/**
 * Class that handles all interactions with the Database Fuseki
 * */
@Service
public class JenaService implements JenaServiceInterface {

  private static final Logger LOGGER = LoggerFactory.getLogger(JenaService.class);

  FusekiConverter fusekiConverter;

  final
  FusekiConnectionAccessor accessor;

  @Autowired
  public JenaService(FusekiConnectionAccessor accessor) {
    this.fusekiConverter = new FusekiConverter();
    this.accessor = accessor;
  }

  public ModelDto getModel(String graphName) {
    try (RDFConnection conn=RDFConnection.connect(accessor.getFusekiEndpoint())) {
      // It is recommended to use Transactions (Txn)
      Model model=conn.fetch(graphName);
      //Model model=Txn.calculateRead(conn, () -> conn.fetch(graphName));
      return new ModelDto(graphName, model);
    }catch (HttpException e){
      return new ModelDto(graphName, null);
    }
  }

  public void removeGraph(String graphName) {
    try (RDFConnection conn=RDFConnection.connect(accessor.getFusekiEndpoint())) {
      // It is recommended to use Transactions (Txn)
      conn.delete(graphName);
      //Txn.executeWrite(conn, () -> conn.delete(graphName));
    }catch (HttpException e){
    }
  }

  // Load adds/appends the new model to the current model
  public ModelDto appendModel(String graphName, Model model) {
    try (RDFConnection conn=RDFConnection.connect(accessor.getFusekiEndpoint())) {
      // Jena automatically creates 4 prefixes
      // It is recommended to use Transactions (Txn)
      //Txn.executeWrite(conn, () -> conn.load(graphName, model));
      conn.load(graphName, model);
      LOGGER.debug("... Data was uploaded to Fuseki!");
      return new ModelDto(graphName, model);

    }catch (HttpException e){
      return new ModelDto(graphName, null);
    }
  }

  // Put replaces the current model with the new model
  public void updateModel(ModelDto newModel){
    LOGGER.debug("Updating the Model!");
    try (RDFConnection conn=RDFConnection.connect(accessor.getFusekiEndpoint())) {
      // It is recommended to use Transactions (Txn)
      //Txn.executeWrite(conn, () -> conn.put(newModel.getName().toString(), newModel.getModel()));
      conn.put(newModel.getName().toString(), newModel.getModel());

    } catch (HttpException e){}
  }

  /**
   * Handles the SELECT Queries from a QueryMessage
   * @param query query to execute
   * @return results*/
  @Override
  public ResultSet select(Query query){
    LOGGER.debug("Executing Query on "+accessor.getFusekiEndpoint());
    // Execute the query
    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())) {

      // Documentation on Transactions on https://jena.apache.org/documentation/rdfconnection/
      /*ResultSet resultSetCopy =
          Txn.calculateRead(conn, () -> {
            ResultSet rs = conn.query(query).execSelect() ;
            return ResultSetFactory.copyResults(rs) ;
          });*/

      ResultSet rs = conn.query(query).execSelect() ;

      LOGGER.debug("... Query executed.");
      LOGGER.debug("Sending response...");

      return ResultSetFactory.copyResults(rs) ;
    } catch ( QueryExceptionHTTP e) {
      logException(e);
      throw new BadRequestException("Error executing query: \n"+e.getMessage());
    }
  }

  /**
   * Handles the DESCRIBE Queries from a QueryMessage
   * @param query query to execute
   * @return model */
  public Model describe(Query query){
    LOGGER.debug("Executing Query on "+accessor.getFusekiEndpoint());

    // Execute the query
    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())) {

      // It is recommended to use Transactions
      //Model resultModel = Txn.calculateRead(conn, () -> conn.queryDescribe(query));
      Model resultModel = conn.queryDescribe(query);

      LOGGER.debug("Sending response...");
      return resultModel;

    } catch ( QueryExceptionHTTP e) {
      logException(e);
      throw new BadRequestException("Error executing query: \n"+e.getMessage());
    }
  }

  /**
   * Handles the CONSTRUCT Queries from a QueryMessage
   * @param query query to execute
   * @return resulting model*/
  @Override
  public Model construct(Query query){
    LOGGER.debug("Executing Query on "+accessor.getFusekiEndpoint());

    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())){
      // It is recommended to use Transactions
      //Model resultModel = Txn.calculateRead(conn, () -> conn.queryConstruct(query));
      Model resultModel = conn.queryConstruct(query);

      LOGGER.debug("Sending response...");

      return resultModel;
    }catch ( QueryExceptionHTTP e) {
      logException(e);
      throw new BadRequestException("Error executing query: \n"+e.getMessage());
    }
  }

  /**
   * Handles the ASK Queries from a QueryMessage
   * @param query query to execute
   * @return boolean */
  @Override
  public boolean ask(Query query){
    LOGGER.debug("Executing Query on "+accessor.getFusekiEndpoint());

    // Execute the query
    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())) {
      // It is recommended to use Transactions
     // boolean result = Txn.calculateRead(conn, () -> conn.queryAsk(query));
      boolean result = conn.queryAsk(query);

      LOGGER.debug("Sending response...");

      return result;

    } catch ( QueryExceptionHTTP e) {
      logException(e);
      throw new BadRequestException("Error executing query: \n"+e.getMessage());
    }
  }

  private static void logException(Exception e){
    LOGGER.error("Error executing query: ");
    LOGGER.error(e.getMessage());
  }

  /**
   * Queries the Database for a SELECT that returns a list with a specific element (i.e., sibject)
   * @param query the query
   * @param elementToSearchFor the element from the query (e.g., 's' or 'g')
   * */
  public  List<String>  select(Query query, String elementToSearchFor){
    LOGGER.debug("Executing Query on "+accessor.getFusekiEndpoint());
    List<String> toReturn;

    // Execute the query
    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())) {

      // Documentation on Transactions on https://jena.apache.org/documentation/rdfconnection/
      List<String> listToReturn = new ArrayList<>();

        // Process results by row:
        conn.querySelect(query, (qs) -> {
          listToReturn.add(qs.getResource(elementToSearchFor).toString());
        });
      /*toReturn = Txn.calculateRead(conn, () -> {
        // Process results by row:
        conn.querySelect(query, (qs) -> {
          listToReturn.add(qs.getResource(elementToSearchFor).toString());
        });
        return listToReturn;
      });*/

      LOGGER.debug("... Query executed.");
      LOGGER.debug("Sending response...");

      return listToReturn;
    } catch ( QueryExceptionHTTP e) {
      logException(e);
      throw new BadRequestException("Error executing query: \n"+e.getMessage());
    }
  }

  public List<Statement> listConnectorCatalogs(String graphName) {
    return this.getModel(graphName).getModel().listStatements( ResourceFactory.createResource(graphName.toString()),
            ResourceFactory.createProperty(core+"resourceCatalog"),
            (RDFNode) null)
        .toList();
  }
}
