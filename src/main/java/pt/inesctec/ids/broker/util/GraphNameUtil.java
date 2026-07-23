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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static pt.inesctec.ids.broker.config.FusekiConstants.CONNECTORS;

public class GraphNameUtil {

  /**
   * Receives the complete URL (e.g., http://localhost:3031/connectorData/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * @return a list of the connectors unique ID's (e.g., producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * */
  public static List<String> urlList2UniqueIdentifierList(List<String> list) {

    String strToCut="/connectors/";
    List<String> ids=new ArrayList<>();

    list.forEach(v->{
      String id=v.substring(v.lastIndexOf(strToCut) + strToCut.length()).trim();
      ids.add(id);
    });

    return ids;
  }

  /**
   * Receives the complete URL (e.g., http://localhost:3031/connectorData/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * @return a list of the connectors unique ID's (e.g., connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * */
  public static List<String> urlList2ShortConnectorURLList(List<String> list) {

    String strToCut="/connectors/";
    List<String> ids=new ArrayList<>();

    list.forEach(v->{
      String id=v.substring(v.lastIndexOf(strToCut) + strToCut.length()).trim();
      ids.add(CONNECTORS + "/"+id);
    });

    return ids;
    
  }

  /**
   * Method is used to extract the connector id to then name the graphs.
   * Receives the complete ConnectorID URL (e.g., https://test-broker.com/id/test/producer-01)
   * @return the connector unique ID (e.g., producer-01)
   * */
  public static String getConnectorUniqueIDFromConnectorURL(URI url) {
    return url.toString().substring(url.toString().lastIndexOf("/") + 1).trim();
  }

  /**
   * Receives the graph url (e.g., http://localhost:3031/connectorData/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * @return the graph unique identifier (e.g., producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * */
  public static String getUniqueIdentifier(URI url) {
    String strToCut="/connectors/";
    return url.toString().substring(url.toString().lastIndexOf(strToCut) + strToCut.length()).trim();
  }

  /**
   * Receives the graph url (e.g., http://localhost:3031/connectorData/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * @return the graph url to work with models (e.g., connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * */
  public static String getShortConnectorURL(URI url) {
    return CONNECTORS + "/"+getUniqueIdentifier(url);
  }


  /**
   * Receives the graph name (e.g., producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * @return the graph name (e.g., http://localhost:3031/connectors/producer-01/2571a527-845c-4237-b384-2b732c58cead)
   * */
  public static String getConnectorGraphName(FusekiConnectionAccessor accessor, String graphName) {
    //return accessor.getFusekiServer()+SEPARATOR+graphName;
    return accessor.getCatalogUri()+graphName;
  }


}
