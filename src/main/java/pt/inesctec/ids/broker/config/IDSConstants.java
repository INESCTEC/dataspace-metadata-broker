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

/**
 * This class contains static constants for configuring fuseki endpoints and graphs.
 */
public class IDSConstants {
  public static final String core="https://w3id.org/idsa/core/";

  public static final String CONNECTOR=core+"BaseConnector";
  public static final String TRUSTED_CONNECTOR=core+"TrustedConnector";

  public static final String GRAPH_IS_ACTIVE=core+"graphIsActive";
  public static final String DEPTH=core+"depth";

  public static final String RESOURCE_CATALOG=core+"ResourceCatalog";
  public static final String OFFERED_RESOURCE=core+"OfferedResource";
  public static final String RESOURCE=core+"Resource";
  public static final String ARTIFACT=core+"Artifact";
  public static final String CONNECTOR_ENDPOINT=core+"ConnectorEndpoint";
  public static final String PUBLIC_KEY=core+"PublicKey";

  public static final String ACCESS_URL=core+"accessURL";
  public static final String KEY_VALUE=core+"keyValue";
  public static final String KEY_TYPE=core+"keyType";



  public static final String SAME_AS="http://www.w3.org/2002/07/owl#sameAs";

  public static final String HEADER = "header";
  public static final String PAYLOAD = "payload";
}
