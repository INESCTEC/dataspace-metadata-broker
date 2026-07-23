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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FusekiConfig {

  @Value("${fuseki.server}")
  private String fusekiServer;

  @Value("${fuseki.datasetName}")
  private String datasetName;

  @Value("${fuseki.username}")
  private String username;

  @Value("${fuseki.password}")
  private String password;

  @Value("${broker.catalog.uri}")
  private String catalogURI;

  @Bean("Endpoint")
  public String getFusekiEndpoint() {
    return fusekiServer+"/"+datasetName;
  }
  @Bean("Server")
  public String getFusekiServer() {
    return fusekiServer;
  }

  @Bean ("Dataset")
  public String getDatasetName() {
    return datasetName;
  }
  @Bean("Username")
  public String getUsername() {
    return username;
  }

  @Bean("Password")
  public String getPassword() {
    return password;
  }

  @Bean("AdminGraphName")
  public String getFusekiAdminGraph() {
    return "https://broker.ids.inesctec.pt/admin";
    //return "http://localhost:8082/"+datasetName+SEPARATOR+"admin";
  }

  @Bean("catalogURI")
  public String getCatalogURI() {
    return catalogURI;
  }

  @Override
  public String toString() {
    return "FusekiConfig{" +
        "fusekiServer='" + fusekiServer + '\'' +
        ", datasetName='" + datasetName + '\'' +
        ", username='" + username + '\'' +
        ", password='" + password + '\'' +
        ", catalogURI='" + catalogURI + '\'' +
        '}';
  }
}
