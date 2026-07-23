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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pt.inesctec.ids.broker.config.FusekiConfig;


@Component
/*@Scope("singleton")*/
public class FusekiConnectionAccessor {

  /*@Qualifier("Server")
  @Autowired
  private String fusekiServer;

  @Qualifier("Dataset")
  @Autowired
  private String datasetName;

  @Qualifier("Username")
  @Autowired
  private String username;

  @Qualifier("Password")
  @Autowired
  private String password;

  @Qualifier("Endpoint")
  @Autowired
  private String fusekiEndpoint;

  @Qualifier("AdminGraphName")
  @Autowired
  private String adminGraphName;

  @Qualifier("catalogURI")
  @Autowired
  private String catalogUri;*/

  private final FusekiConfig fusekiConfig;

  @Autowired
  public FusekiConnectionAccessor(FusekiConfig fusekiConfig) {
    this.fusekiConfig = fusekiConfig;
  }

  public HttpClient authenticate(){
    CredentialsProvider provider = new BasicCredentialsProvider();
    provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(fusekiConfig.getUsername(), fusekiConfig.getPassword()));

    // Create an HttpClient with the Basic Authentication credentials
    return HttpClients.custom()
            .setDefaultCredentialsProvider(provider)
            .build();
  }

  public Dataset getDataset() {
    return DatasetFactory.create(fusekiConfig.getFusekiEndpoint());
  }

  public String getDatasetName() {
    return fusekiConfig.getDatasetName();
  }

  public String getUsername() {
    return fusekiConfig.getUsername();
  }

  public String getPassword() {
    return fusekiConfig.getPassword();
  }

  public String getFusekiServer(){return fusekiConfig.getFusekiServer();}

  public String getFusekiEndpoint() {
    return fusekiConfig.getFusekiEndpoint();
    //return fusekiEndpoint;
  }

  public String getAdminGraphName() {
    return fusekiConfig.getFusekiAdminGraph();
  }

  public String getCatalogUri() {
    return fusekiConfig.getCatalogURI();
  }

  @Override
  public String toString() {
    return fusekiConfig.toString();
  }
}
