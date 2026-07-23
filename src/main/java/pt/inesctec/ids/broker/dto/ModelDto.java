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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class ModelDto {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelDto.class);

  public ModelDto(URI name, Model model) {
    this.name = name;
    this.model = model;
  }

  public ModelDto(String name, Model model) {
    this.name = URI.create(name);
    this.model = model;
  }

  /**
   * GraphName
   */
  public URI name;

  /* Model */
  public Model model;

  public List<Statement> listStatementsWithSubjectEqualTo(URI subject) {
    return this.getModel().listStatements(ResourceFactory.createResource(String.valueOf(subject)), null, (Resource) null).toList();
  }

  public List<Statement> listStatementsWithObjectEqualTo(URI object) {
    return this.getModel().listStatements(null, null, ResourceFactory.createResource(String.valueOf(object))).toList();
  }
  public List<Statement> listStatementsWithObjectEqualTo(boolean object) {
    return this.getModel().listStatements(null, null, ResourceFactory.createTypedLiteral(object)).toList();
  }

  public List<Statement> mapInternalIDsWithIDs(URI id) {
    return this.getModel().listStatements(null,
                    ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
                    ResourceFactory.createResource(String.valueOf(id)))
            .toList();
  }

  /** Maps the Internal ID with the IDS IDs
  *
  * @param id (e.g., graphName)
   * @return list of statements with that ID (e.g., [[http://test-hostname:3032/connectors/connector-producer/-1374631466, http://www.w3.org/2002/07/owl#sameAs, https://test-broker.com/id/test/multipleCatalogs/connector-producer]])
  * */
  public List<Statement> mapIDsWithInternalIDs(URI id) {
    return this.getModel().listStatements( ResourceFactory.createResource(String.valueOf(id)),
                    ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"),
                    (RDFNode) null)
            .toList();
  }

}
