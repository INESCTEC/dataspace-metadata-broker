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
import de.fraunhofer.iais.eis.ids.component.core.RejectMessageException;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.RdfResource;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import ids.messaging.common.SerializeException;
import ids.messaging.response.MessageResponse;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.exceptions.IsSelfDescriptionException;
import pt.inesctec.ids.broker.util.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static pt.inesctec.ids.broker.config.IDSConstants.*;

@Service
public class DescriptionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DescriptionService.class);

  private int defaultDepth=0;
  final
  FusekiConnectionAccessor accessor;

  public DescriptionService(FusekiConnectionAccessor accessor) {
    this.accessor = accessor;
  }

  public Model description(String catalogUri,
                           URI requestedElement,
                           FusekiConnectionAccessor accessor,
                           int depth) throws RejectMessageException, IsSelfDescriptionException {

    // IF there is no elementID it means to return the Broker public self-description
    if (requestedElement == null) {
      LOGGER.info("Is returning the Broker public self-description");
      throw new IsSelfDescriptionException();
    }

    StringBuilder queryString = new StringBuilder();
    queryString.append("PREFIX ids: <https://w3id.org/idsa/core/> \n");
    queryString.append("PREFIX owl: <http://www.w3.org/2002/07/owl#> \n"); //sameAs statements

    //Check if we are at the root. This top path, which is the catalog URI to the outside, is not persisted as such in the triple store, but generated upon request
    //If the root URI has been requested, we need to create specific SPARQL CONSTRUCT queries to serve a connector catalog
    boolean isConnectors = false;

    //In this case, if it is the /connectors/ it only shows the list of connectors. Otherwise, go deeper into the Database
    if (requestedElement.toString().equals(catalogUri) || (requestedElement+"/").equals(catalogUri)) {
      LOGGER.info("Catalog has been requested (with depth: " + depth + "): " + requestedElement);
      isConnectors = true;
      queryString.append("CONSTRUCT { <").append(catalogUri).append("> a ids:ConnectorCatalog ; ids:listedConnector ?s0 . ?s0 ?p0 ?o0 .");

    }
    // Go deeper into the Database
    else {
      LOGGER.info("Custom element has been requested (with depth  " + depth + "): " + requestedElement);
      queryString.append("CONSTRUCT { ?s0 ?p0 ?o0 . ");
    }

    // depth defines the limit to search into the database
    for(int i = 0; i < depth; i++) {
      queryString.append("?o").append(i).append(" ?p").append(i + 1).append(" ?o").append(i + 1).append(" . ");
    }

    //Close CONSTRUCT brackets
    queryString.append(" } ");

    // Get list of active connectors, and add all of them to the FROM NAMED <GRAPH> command
    AdminGraphService adminGraphService=new AdminGraphService(accessor);
    List<String> activeGraphs = adminGraphService.getListOfActiveConnectors();
    activeGraphs.forEach(graphName -> queryString.append("FROM NAMED <").append(graphName).append("> "));

    if(activeGraphs.isEmpty())
    {
      //Make sure that there is a FROM statement. If there is no FROM statement, it is assumed that ALL graphs should be used
      //This will result in an empty result set, except for the few triples we have constructed above
      queryString.append("FROM NAMED <http://dummy.org/non-existing-graph> ");
    }
    queryString.append("WHERE { ");

    if(!isConnectors) //Specific element was requested, which we can retrieve "as-is" (unlike the catalog, which we need to generate on the fly)
    {
      queryString.append(" GRAPH ?g {  { ?requestedElement ?p0 ?o0 . } UNION { ?s owl:sameAs ?requestedElement ; ?p0 ?o0 . } ")
          .append(" BIND ( IF (BOUND(?s), ?s, ?requestedElement) AS ?s0) ."); //Make sure that the rewritten URI is used as s0
    }


    else //Need to construct a catalog, so we query for all active connectors
    {
      //First ?s0 ?p0 ?o0 IS in optional block. If catalog is empty, no error should be thrown
      //At this stage, the ontology class hierarchy is not respected in queries. Therefore, we will list all Connector types
      queryString.append("GRAPH ?g { OPTIONAL { ?s0 ?p0 ?o0 . ?s0 a ?s0type . FILTER( ?s0type IN ( ids:BaseConnector, ids:TrustedConnector, ids:Connector ) ) . ");
    }

    for(int i = 0; i < depth; i++)
    {
      queryString.append("OPTIONAL { ?o").append(i).append(" ?p").append(i + 1).append(" ?o").append(i + 1).append(" ");
    }
    queryString.append("} ".repeat(Math.max(0, depth)));

    queryString.append("} }"); //Brackets from graph and where

    if(isConnectors)
    {
      //At root, there is one more OPTIONAL
      queryString.append(" }");
    }

    //Fire construct query against triple store
    Model result;
    JenaService jenaService=new JenaService(accessor);
    if(!isConnectors)
    {
        ParameterizedSparqlString parameterizedSparqlString = new ParameterizedSparqlString(queryString.toString());
        parameterizedSparqlString.setIri("requestedElement", requestedElement.toString());
        //System.out.println(parameterizedSparqlString.toString());

        result=jenaService.construct(parameterizedSparqlString.asQuery());
    }
    else
    {
      //System.out.println(queryString);
      result=jenaService.construct(QueryUtil.constructQuery(String.valueOf(queryString)));
    }

    //Check if requested element exists in the Database
    if(result.isEmpty())
    {
      //Result is empty, throw exception. This will result in a RejectionMessage being sent
      throw new RejectMessageException(RejectionReason.NOT_FOUND, new NullPointerException("The requested resource was not found"));
    }
    //LOGGER.debug(result.toString());
    return result;
  }


  // If no depth is defined, default is 0. This is also defined on the DescriptionRequestHandler.java
  public Model description(String catalogUri,
                           URI requestedElement,
                           FusekiConnectionAccessor accessor) throws RejectMessageException, IsSelfDescriptionException {

    return description(catalogUri, requestedElement, accessor, defaultDepth);

  }

  public int getDepth(DescriptionRequestMessageImpl requestMessage)  {
    return requestMessage.getProperties()==null || requestMessage.getProperties().get(DEPTH)==null ?
        defaultDepth :
        Integer.parseInt(new TypedLiteral(requestMessage.getProperties().get(DEPTH).toString()).getValue());
  }

  public int getDepth(Map<String, String> headers)  {
    // The IDSA Broker does not force the Default as 10. The Connector does
    return headers.get("depth")==null ? defaultDepth : Integer.valueOf(headers.get("depth"));
  }

  public void checkIDSHeaders(Map<String, String> headers) throws BadRequestException {
    if(!headers.containsKey("ids-securitytoken")) {
      throw new BadRequestException("ids-securitytoken");
    }
    if(!headers.containsKey("ids-modelversion")) {
      throw new BadRequestException("ids-modelversion");
    }
    if(!headers.containsKey("ids-issued")) {
      throw new BadRequestException("ids-issued");
    }
    if(!headers.containsKey("ids-senderagent")) {
      throw new BadRequestException("ids-senderagent");
    }
    if(!headers.containsKey("ids-issuerconnector")) {
      throw new BadRequestException("ids-issuerconnector");
    }
  }


  /***
   * Returns the response format to print to the user
   * @param requestMessage a description request message (it is used on the DescriptionRequestHandler.java)
   * @return the requested format
   * */
  public RDFFormat returnOutputFormat(DescriptionRequestMessageImpl requestMessage)  {
    List<String> acceptStrings = new ArrayList<>();
    String ACCEPT="https://w3id.org/idsa/core/accept";


    try {
      Object acceptObject =requestMessage.getProperties().get(ACCEPT);

      // Following the IDSA Documentation, if a DescriptionRequestMessage has an Accept Header, it should be in a TypedLiteral
      if(TypedLiteral.class.isAssignableFrom(acceptObject.getClass()))
      {
        //If it constains Multiple values add all of them to the List of acceptedStrings
        if(((TypedLiteral)acceptObject).getValue().contains(",")) //If it constains Multiple values
        {
          acceptStrings.addAll(Arrays.asList(((TypedLiteral)acceptObject).getValue().split(",")));
        }
        else
        { // Otherwise, add the only value to the list
          acceptStrings.add(((TypedLiteral)acceptObject).getValue());
        }
      }
      else if (ArrayList.class.isAssignableFrom(acceptObject.getClass())) { // If, instead of a TypedLiteral it is a List of TypedLiterals
        // Add the TypedLiterals value to the list of acceptStrings
        acceptStrings = ((ArrayList<TypedLiteral>) requestMessage.getProperties().get(ACCEPT))
            .stream().map(RdfResource::getValue).collect(Collectors.toList());
      }
    }catch (NullPointerException e){ // This is thrown when either no properties or no accept property are defined
    }

    // Remove whitespaces to avoid getting a wrong response when doing the contains
    acceptStrings = acceptStrings.stream()
        .map(String::trim)
        .collect(Collectors.toList());

    // If the message does not contain the property, it returns the default (Because the list of acceptedStrings will be empty)
    return selectFormat(acceptStrings);
  }

  /***
   * Returns the response format to print to the user
   * @param headers the headers form the REST request (it is used on the ConnectorController.java)
   * @return the requested format
   * */
  public RDFFormat returnOutputFormat(Map<String, String> headers)  {

    if(headers.containsKey("accept")){
      ArrayList<String> acceptStrings = new ArrayList<>(List.of(headers.get("accept").split(", ")));

      return selectFormat(acceptStrings);
    }
    return RDFFormat.JSONLD10_COMPACT_PRETTY;

  }

  private RDFFormat selectFormat(List<String> acceptStrings){
    RDFFormat outputLanguage=RDFFormat.JSONLD10_COMPACT_PRETTY;

    if(acceptStrings.contains(ResponseType.TURTLE)) {
      //Turtle is the 1st priority (even thout the method should not call Turtle) -> It is mandatory according to the specification
      outputLanguage = RDFFormat.TURTLE;
    } else if (acceptStrings.contains(ResponseType.JSON_LD)) {
      outputLanguage = RDFFormat.JSONLD10_COMPACT_PRETTY; //Default value. if Turtle was not explicitly requested (2nd priority)
    }
    else if(acceptStrings.contains(ResponseType.NTRIPLES)) //Check for n-triples (3rd priority)
    {
      outputLanguage = RDFFormat.NTRIPLES;
    }
    else if(acceptStrings.contains(ResponseType.RDF_XML)) //4th priority is RDF XML
    {
      outputLanguage = RDFFormat.RDFXML;
    }
    return outputLanguage;
  }

  public String getPayloadFromMessageResponse(MessageResponse messageResponse) {
    return IDSMessageUtil.extractPayloadFromMap(getMapFromMessageResponse(messageResponse));
  }


  public String getHeaderFromMessageResponse(MessageResponse messageResponse) {
    return IDSMessageUtil.extractHeaderFromMap(getMapFromMessageResponse(messageResponse));
  }

  public Map<String, Object> getMapFromMessageResponse(MessageResponse messageResponse){
    Serializer serializer=new Serializer();
    try {
      return messageResponse.createMultipartMap(serializer);
    } catch (SerializeException e) {
      // TODO: handle this
      throw new RuntimeException(e);
    }
  }

}
