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
package pt.inesctec.ids.broker.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import pt.inesctec.ids.broker.builder.TriplesBuilder;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.dto.Triples;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.JenaService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.CompareUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.FusekiUtil;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.FusekiConstants.SEPARATOR;
import static pt.inesctec.ids.broker.config.IDSConstants.GRAPH_IS_ACTIVE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class FusekiConverterTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(FusekiConverterTest.class);

  BrokerService service;
  ModelService modelService;
  AdminGraphService adminGraphService;
  FusekiConverter converter=new FusekiConverter();

  @Autowired
  FusekiConnectionAccessor accessor;

  JenaService jenaService;

  TestUtil testUtil;

  String select;
  Triples graph;

  ModelDto adminModel;
  String separator=FileSystems.getDefault().getSeparator();


  @BeforeEach
  void setUp() throws IOException{
    service=new BrokerService(accessor);
    adminGraphService=new AdminGraphService(accessor);
    modelService=new ModelService(accessor);
    testUtil=new TestUtil();
    jenaService=new JenaService(accessor);


    testUtil.removeAllGraphsFromDB(accessor);
    // Removing the Admin graph
    testUtil.removeAdminGraph(accessor);
    adminGraphService.initializeAdminGraph();

    // Gets the AdminModel
    graph = new TriplesBuilder()
            .subject("https://subject/connectors")
            .predicate(GRAPH_IS_ACTIVE)
            .object(true)
            .build();

    adminModel=new ModelDto(accessor.getAdminGraphName(), adminGraphService.addAdminModel(graph));

    // Adds the Model to the DB
    Dataset dataset = DatasetFactory.create();
    dataset.addNamedModel(adminModel.getName().toString(), adminModel.getModel());

    // Save the model to Fuseki
    //accessor.connect().load(adminModel.getName().toString(), adminModel.getModel());
    jenaService.appendModel(adminModel.getName().toString(), adminModel.getModel());

    select="SELECT ?subject ?predicate ?object\n" +
            "WHERE {\n" +
            "  GRAPH <"+adminModel.getName()+"> { ?subject ?predicate ?object }\n" +
            "}";
  }

  @Test
  void modelToJson() {
    String result="{\"@id\":\"https://subject/connectors\",\"ids:graphIsActive\":{\"@value\":\"true\",\"@type\":\"http://www.w3.org/2001/XMLSchema#boolean\"},\"@context\":{\"rdf\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns\",\"ids\":\"https://w3id.org/idsa/core/\",\"idsc\":\"https://w3id.org/idsa/code/\"}}";
    JSONObject json=converter.modelToJson(adminModel.getModel());
    System.out.println(json);
    assertEquals(json.toString(), result.replace("\\/", separator));
  }
 @Test
  void modelToTurleFormat() {
    String lineSeparator=System.lineSeparator();
    String result="@prefix ids: <https://w3id.org/idsa/core/> ."+lineSeparator+"" +
        "@prefix idsc: <https://w3id.org/idsa/code/> ." +lineSeparator+"" +
        "@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns> ."+lineSeparator+"" +
            ""+lineSeparator+"" +
            "<https://subject/connectors>  ids:graphIsActive  true ."+lineSeparator+"" +
            "";
    StringWriter sw=converter.modelToTurleFormat(adminModel.getModel());
    System.out.println(sw.toString()
        .replaceAll("(.{64})", "$1\n")
        .replaceAll("\n", "")
        .replaceAll(lineSeparator, ""));

    assertEquals(TestUtil.replaceBlankSpacesWithOneSpace(sw.toString().replaceAll("\n", " ")),
        TestUtil.replaceBlankSpacesWithOneSpace(result));
  }

  @Test
  void resultSet2Json() throws Exception {
    String expectedResult="[\n" +
            "  {\n" +
            "    \"predicate\": \"https://w3id.org/idsa/core/graphIsActive\",\n" +
            "    \"subject\": \"https://subject/connectors\",\n" +
            "    \"object\": \"true^^http://www.w3.org/2001/XMLSchema#boolean\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"predicate\": \"https://w3id.org/idsa/core/graphIsActive\",\n" +
            "    \"subject\": \"https://broker.ids.inesctec.pt/admin\",\n" +
            "    \"object\": \"false^^http://www.w3.org/2001/XMLSchema#boolean\"\n" +
            "  }\n" +
            "]";
    ResultSet results = jenaService.select(QueryFactory.create(select));
    String string=converter.resultSet2Json(results);

    JsonConverter jsonConverter=new JsonConverter();
    JsonNode resultAsJson=jsonConverter.string2Json(string);
    System.err.println(resultAsJson.toPrettyString());

    JsonNode expectedResultAsJson=jsonConverter.string2Json(expectedResult);

    assertTrue(CompareUtil.compareJsonNodes((ArrayNode) resultAsJson, (ArrayNode) expectedResultAsJson));
  }

  @Test
  void resultSet2ByteArray() throws Exception {
    String result="{ \"head\": {\n" +
            "    \"vars\": [ \"subject\" , \"predicate\" , \"object\" ]\n" +
            "  } ,\n" +
            "  \"results\": {\n" +
            "    \"bindings\": [\n" +
            "      { \n" +
            "        \"subject\": { \"type\": \"uri\" , \"value\": \"https://subject/connectors\" } ,\n" +
            "        \"predicate\": { \"type\": \"uri\" , \"value\": \"https://w3id.org/idsa/core/graphIsActive\" } ,\n" +
            "        \"object\": { \"type\": \"literal\" , \"datatype\": \"http://www.w3.org/2001/XMLSchema#boolean\" , \"value\": \"true\" }\n" +
            "      } ,\n" +
            "      { \n" +
            "        \"subject\": { \"type\": \"uri\" , \"value\": \"https://broker.ids.inesctec.pt/admin\" } ,\n" +
            "        \"predicate\": { \"type\": \"uri\" , \"value\": \"https://w3id.org/idsa/core/graphIsActive\" } ,\n" +
            "        \"object\": { \"type\": \"literal\" , \"datatype\": \"http://www.w3.org/2001/XMLSchema#boolean\" , \"value\": \"false\" }\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}" +
            "\n";
    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())) {
      ResultSet results = conn.query(select).execSelect();
      String string= converter.resultSet2ByteArray(results);
      System.out.println(string);

      ObjectMapper mapper = new ObjectMapper();
      // Parse the JSON strings into JsonNodes
      JsonNode stringAsJsonNode = mapper.readTree(string);
      JsonNode resultAsJsonNode = mapper.readTree(result);

      System.err.println(stringAsJsonNode.toPrettyString());
      System.out.println(resultAsJsonNode.toPrettyString());

      // Compare two ByteArrays
      assertTrue(stringAsJsonNode.get("head").equals(resultAsJsonNode.get("head")));
      ArrayNode bindings1 = (ArrayNode) stringAsJsonNode.get("results").get("bindings");
      ArrayNode bindings2 = (ArrayNode) resultAsJsonNode.get("results").get("bindings");
      assertTrue(CompareUtil.compareJsonNodes(bindings1, bindings2));
    }
    catch (QueryParseException e) {
      LOGGER.error("Error parsing query: ");
      LOGGER.error(e.getMessage());
      throw new BadRequestException("Error parsing query: \n"+e.getMessage());
    }
  }

  @Test
  void model2Json() {
    String result="{\"@id\":\"https://subject/connectors\",\"ids:graphIsActive\":{\"@value\":\"true\",\"@type\":\"http://www.w3.org/2001/XMLSchema#boolean\"},\"@context\":{\"rdf\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns\",\"ids\":\"https://w3id.org/idsa/core/\",\"idsc\":\"https://w3id.org/idsa/code/\"}}";
    JSONObject json=converter.modelToJson(adminModel.getModel());
    System.out.println(json);
    System.err.println(result.replace("\\/", separator));
    assertEquals(json.toString(), result.replace("\\/", separator));
  }

  @Test
  void orderedTriples2Screen() {
    try (RDFConnection conn = RDFConnection.connect(accessor.getFusekiEndpoint())) {
      ResultSet results = conn.query(select).execSelect();
      converter.orderedTriples2Screen(results, QueryFactory.create(select));
    }
    catch (QueryParseException e) {
      LOGGER.error("Error parsing query: ");
      LOGGER.error(e.getMessage());
      throw new BadRequestException("Error parsing query: \n"+e.getMessage());
    }
  }


  @Test
  void modelToString() {

    String lineSeparator = System.lineSeparator();
    String result="{\n" +
        "    \"@id\": \"https://subject/connectors\",\n" +
        "    \"ids:graphIsActive\": {\n" +
        "        \"@value\": \"true\",\n" +
        "        \"@type\": \"http://www.w3.org/2001/XMLSchema#boolean\"\n" +
        "    },\n" +
        "    \"@context\": {\n" +
        "        \"ids\": \"https://w3id.org/idsa/core/\",\n" +
        "        \"idsc\": \"https://w3id.org/idsa/code/\",\n" +
        "        \"rdf\": \"http://www.w3.org/1999/02/22-rdf-syntax-ns\"\n" +
        "    }\n" +
        "}";

    String string=converter.modelToString(adminModel.getModel());
    System.out.println(string);

    assertThat(string
          .replaceAll("(.{64})", "$1\n")
          .replaceAll("\n", "")
          .replaceAll(lineSeparator, ""))
        .isEqualTo(result.replaceAll("(.{64})", "$1\n")
          .replaceAll("\n", "")
          .replaceAll(lineSeparator, ""));

  }

  @Test
  void model2ListOfStatements() {
    List<Statement> json=converter.model2ListOfStatements(adminModel.getModel());
    System.out.println(json);
    assertEquals(json, List.of(graph.toGraphIsActiveStatement()));
  }

  @Test
  void listOfStatements2Model() {
    Triples graph = new TriplesBuilder()
            .subject("subject")
            .predicate(GRAPH_IS_ACTIVE)
            .object(true)
            .build();
    Model resultModel=adminGraphService.addAdminModel(graph);
    Model newModel=converter.listOfStatements2Model(List.of(graph.toGraphIsActiveStatement()));
    Assertions.assertEquals(FusekiUtil.getStatementsDiff(newModel, resultModel).hasDiff(), false );
  }

}