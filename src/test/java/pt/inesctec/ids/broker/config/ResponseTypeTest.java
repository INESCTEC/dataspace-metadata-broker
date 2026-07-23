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
package pt.inesctec.ids.broker.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTypeTest {

  @Test
  void checkIfExists() {
    assertTrue(ResponseType.checkIfExists(ResponseType.JSON_LD));
    assertTrue(ResponseType.checkIfExists(ResponseType.JSON));
    assertTrue(ResponseType.checkIfExists(ResponseType.CSV));
    assertTrue(ResponseType.checkIfExists(ResponseType.TSV));
    assertTrue(ResponseType.checkIfExists(ResponseType.TEXT));
    assertFalse(ResponseType.checkIfExists("invalid"));
  }
}