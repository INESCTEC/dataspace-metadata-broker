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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(
        locations = "classpath:application-test.properties")
class GraphNameUtilTest {

  @Autowired
  FusekiConnectionAccessor accessor;

  @BeforeEach
  void setUp() {
  }


  @Test
  void getConnectorUniqueIDFromConnectorURL() {
    String expectedResponse="producer-01";
    URI connectorID=URI.create("https://test-broker.com/id/test/producer-01");
    String response= GraphNameUtil.getConnectorUniqueIDFromConnectorURL(connectorID);

    assertEquals(expectedResponse, response);

  }

  @Test
  void getUniqueIdentifier() {
    String expectedResponse="producer-01/2571a527-845c-4237-b384-2b732c58cead";
    URI connectorID=URI.create("http://localhost:3031/connectorData/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead");
    String response= GraphNameUtil.getUniqueIdentifier(connectorID);

    assertEquals(expectedResponse, response);
  }

  @Test
  void getShortConnectorURL() {
    String expectedResponse="connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead";
    URI connectorID=URI.create("http://localhost:3031/connectorData/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead");
    String response= GraphNameUtil.getShortConnectorURL(connectorID);

    assertEquals(expectedResponse, response);
  }

  @Test
  void getConnectorGraphName() {
    String expectedResponse="http://test-hostname:8082/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead";
    String response= GraphNameUtil.getConnectorGraphName(accessor, "producer-01/2571a527-845c-4237-b384-2b732c58cead");

    assertEquals(expectedResponse, response);
  }
}