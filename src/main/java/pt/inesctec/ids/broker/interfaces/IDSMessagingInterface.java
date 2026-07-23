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
package pt.inesctec.ids.broker.interfaces;

import de.fraunhofer.iais.eis.Connector;
import org.apache.jena.rdf.model.Model;

import java.net.URI;
import java.util.List;

public interface IDSMessagingInterface {

  /* ConnectorUpdateMessage */
  String addConnectorToFuseki(Connector connector);

  /* ConnectorUnavailableMessage */
  void makeConnectorUnavailable(URI connectorID);

  /* Query */
  String executeSPARQLQuery(String receivedPayload, String returnType);

  /* ResourceUpdateMessage */
  String addResourceToFuseki(de.fraunhofer.iais.eis.Resource resource, URI connectorID);

  /* ResourceUnavailableMessage */
  Model removeResourceFromFuseki(URI connectorID, URI resourceID);

}
