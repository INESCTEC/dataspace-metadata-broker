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
package pt.inesctec.ids.broker.dto;

import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.inesctec.ids.broker.builder.TriplesBuilder;
import pt.inesctec.ids.broker.util.TestUtil;

import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.GRAPH_IS_ACTIVE;

class TriplesTest {
  Triples triple;

  @BeforeEach
  void init(){
    triple = new Triples("graphName", GRAPH_IS_ACTIVE, true);
  }

  @Test
  void toJson() {
    String expectedJson="{\n" +
            "  \"subject\": \"graphName\",\n" +
            "  \"predicate\": \"https://w3id.org/idsa/core/graphIsActive\",\n" +
            "  \"object\": true\n" +
            "}\n";


    System.out.println(triple.toJson());
    assertEquals(TestUtil.replaceSeparator(expectedJson), TestUtil.replaceSeparator(triple.toJson()));
  }

  @Test
  void toStatement() {
    String expectedStatement="[graphName, https://w3id.org/idsa/core/graphIsActive, \"true\"]";

    Statement resultStatement=triple.toStatement();
    System.out.println(resultStatement);
    assertEquals(expectedStatement, resultStatement.toString());
  }

  @Test
  void toGraphIsActiveStatement() {
    String expectedStatement="[graphName, https://w3id.org/idsa/core/graphIsActive, \"true\"^^http://www.w3.org/2001/XMLSchema#boolean]";

    Statement resultStatement=triple.toGraphIsActiveStatement();
    System.out.println(resultStatement);
    assertEquals(expectedStatement, resultStatement.toString());
  }
}