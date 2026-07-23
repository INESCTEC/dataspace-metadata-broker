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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.ConnectorCatalog;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class ConnectorsDTO {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorsDTO.class);


  //@JsonProperty
  public ConnectorCatalog connectorCatalog;

  //@JsonProperty
  public List<Connector> connectorList;


  public ConnectorsDTO(ConnectorCatalog connectorCatalog, List<Connector> connectorList) {
    this.connectorCatalog = connectorCatalog;
    this.connectorList = connectorList;
  }

  @Override
  public String toString() {
    return "ConnectorsDTO{" +
            "connectorCatalog=" + connectorCatalog +
            ", connectorList=" + connectorList +
            '}';
  }

  public String toJson() {
    System.out.println(this);
    Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    String json = gson.toJson(this);
    return json;
    /*JsonConverter jsonConverter = new JsonConverter();
    try {
      return jsonConverter.object2Json(this).toPrettyString().toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }*/
  }
}
