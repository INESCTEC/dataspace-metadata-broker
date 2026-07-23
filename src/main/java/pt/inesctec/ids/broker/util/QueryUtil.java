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

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.service.AdminGraphService;


public class QueryUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryUtil.class);

  /**
   * Checks if the Query has a WHERE clause
   * Adds the Named Graphs
   * @param query the query.
   * */
  public static Query rewriteQuery(Query query, FusekiConnectionAccessor accessor) {

    // Checks if Query has FROM clause. It should never have a FROM
    if (query.getDatasetDescription() != null) {
      LOGGER.error("Query has a FROM clause. An error will be thrown.");
      // If there is a FROM clause, an error is thrown
      throw new BadRequestException("Invalid query. A FROM clause is not allowed.");
    }

    // Get list of active connectors, and add all of them to the FROM NAMED <GRAPH> command
    AdminGraphService adminGraphService=new AdminGraphService(accessor);
    adminGraphService.getListOfActiveConnectors().forEach(graph ->query.addNamedGraphURI(graph));

    // If there are no Active Graphs
    if(query.getNamedGraphURIs().isEmpty()){
      LOGGER.info("There are no active graphs");
      return null;
    }

    // Creates the GRAPH ?__RESERVED statement. This Statement allows to query all graphs
    ElementNamedGraph graphReserved = new ElementNamedGraph(
            NodeFactory.createVariable("__RESERVED"),
            query.getQueryPattern()
    );


    /* Check if the query has a WHERE clause.
     If it doesn't (==null), throw an error. This never happens, since the constructQuery() already prevents it
     If it does, append the GRAPH ?__RESERVED statement.
    * */
    if (query.getQueryPattern() == null) {
      // This never happens, since the constructQuery() already prevents it. However, throw an error
      LOGGER.error("Query does not have a WHERE clause...");
      throw new BadRequestException("Invalid query. A WHERE clause is mandatory.");
    } else {
      LOGGER.debug("Query has a WHERE clause...");
      // Checks if the Query already has the Graph Reserved command
      if(query.getQueryPattern().toString().toUpperCase().contains("GRAPH") &&
              query.getQueryPattern().toString().toUpperCase().contains("?__RESERVED")){
        return query;
      }
      LOGGER.debug("... The Graph command will be appended.");

      ElementGroup elementGroup = new ElementGroup();

      // Add the GRAPH ?__RESERVED statement to the new query pattern
      elementGroup.addElement(graphReserved);

      // Set the new statement as the query's pattern
      query.setQueryPattern(elementGroup);
    }
    return query;
  }

  /**
   * Makes sure the query does not have a FROM clause
   * Calls rewriteQuery() to handle the WHERE and the Named Graphs
   * @param originalQuery the query.
   * */
  public static Query rewriteSelect(String originalQuery, FusekiConnectionAccessor accessor) {

    Query query=constructQuery(originalQuery);
    //String serializedQuery = query.serialize();
    //if(serializedQuery.contains("WHERE"))

    try {
      query=rewriteQuery(query, accessor);
      // Query can be null is there are no active graphs
      if(query!=null){
        // Adds the DISTINCT to the SELECT
        query.setDistinct(true);
      }
    }catch (BadRequestException exception){
      throw new BadRequestException(exception.getMessage());
    }
    return query;
  }

  /**
   * Checks if the Query has a WHERE clause and adds the Graph ?__RESERVED
   * Adds the Named Graphs
   * @param originalQuery the query.
   * */
  public static Query rewriteConstructAndDescribe(String originalQuery, FusekiConnectionAccessor accessor) {

    Query query=constructQuery(originalQuery);

    try {
      query=rewriteQuery(query, accessor);
    }catch (BadRequestException exception){
      throw new BadRequestException(exception.getMessage());
    }

    return query;
  }

  /**
   * Checks if the Query has a WHERE clause
   * Adds the Named Graphs
   * @param originalQuery the query.
   * */
  public static Query rewriteASK(String originalQuery, FusekiConnectionAccessor accessor) {

    Query query=constructQuery(originalQuery);

    // Checks if Query has FROM clause. It should never have a FROM
    if (query.getDatasetDescription() != null) {
      LOGGER.error("Query has a FROM clause. An error will be thrown.");
      // If there is a FROM clause, an error is thrown
      throw new BadRequestException("Invalid query. A FROM clause is not allowed.");
    }

    // Get list of active connectors, and add all of them to the FROM NAMED <GRAPH> command
    AdminGraphService adminGraphService=new AdminGraphService(accessor);
    adminGraphService.getListOfActiveConnectors().forEach(graph ->query.addNamedGraphURI(graph));

    // If there are no Active Graphs
    if(query.getNamedGraphURIs().isEmpty()){
      LOGGER.info("There are no active graphs");
      return null;
    }

    // Creates the GRAPH ?__RESERVED statement. This Statement allows to query all graphs
    ElementNamedGraph graphReserved = new ElementNamedGraph(
            NodeFactory.createVariable("__RESERVED"),
            query.getQueryPattern()
    );


    /* Check if the query has a WHERE clause.
     If it doesn't (==null), add one (specific behavior to the ASK)
     If it does, append the GRAPH ?__RESERVED statement.
    * */
    if (query.getQueryPattern() == null) {
      // This never happens, since the constructQuery() already prevents it. However, throw an error
      LOGGER.debug("Query does not have a WHERE clause...");
      LOGGER.debug("... One will be added");
      query.setQueryPattern(graphReserved);
    } else {
      LOGGER.debug("Query has a WHERE clause...");
      // Checks if the Query already has the Graph Reserved command
      if(query.getQueryPattern().toString().toUpperCase().contains("GRAPH") &&
              query.getQueryPattern().toString().toUpperCase().contains("?__RESERVED")){
        return query;
      }
      LOGGER.debug("... The Graph command will be appended.");

      ElementGroup elementGroup = new ElementGroup();

      // Add the GRAPH ?__RESERVED statement to the new query pattern
      elementGroup.addElement(graphReserved);

      // Set the new statement as the query's pattern
      query.setQueryPattern(elementGroup);
    }
    return query;
  }

  /**
   *  Creates a Query
   * @return a string in Query format
   * */
  public static Query constructQuery(String sparqlQueryString) {
    // I can modify the query by, for instance, adding query options or prefixes:
    // query.setLimit(20);
    // query.addPrefix("ids", "https://w3id.org/idsa/core/");

    try {
      Query query = QueryFactory.create(sparqlQueryString);
      return query;
    }
    catch (QueryParseException e) {
      LOGGER.error("Error parsing query:");
      LOGGER.error(e.getMessage());
      throw new BadRequestException("Error parsing query. The query is invalid.");
    }
  }




}
