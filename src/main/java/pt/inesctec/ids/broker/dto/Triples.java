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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor
@Getter
@Setter
public class Triples {

  private static final Logger LOGGER = LoggerFactory.getLogger(Triples.class);

  /**
   * Subject
   */
  //@JsonProperty
  public Object subject;

  /* Predicate */
  //@JsonProperty
  public Object predicate;

  /* Object */
  //@JsonProperty
  public Object object;

  public Triples(Object subject, Object predicate, Object object) {
    this.subject = subject;
    this.predicate = predicate;
    this.object = object;
  }

  @Override
  public String toString() {
    return "QueryResult{" +
            "subject=" + subject +
            ", predicate=" + predicate +
            ", object=" + object +
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

  public Statement toStatement(){
    return  ResourceFactory.createStatement( //Add a new triple
            ResourceFactory.createResource(this.subject.toString()), //Subject
            ResourceFactory.createProperty(this.predicate.toString()), //Predicate
            ResourceFactory.createPlainLiteral(this.object.toString())); // Object
  }

  public Statement toGraphIsActiveStatement(){
    return  ResourceFactory.createStatement( //Add a new triple
            ResourceFactory.createResource(this.subject.toString()), //Subject
            ResourceFactory.createProperty(String.valueOf(this.predicate)), //Predicate
            ResourceFactory.createTypedLiteral(this.object)); // Object
  }
}
