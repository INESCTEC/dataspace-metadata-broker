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

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.component.core.InfomodelFormalException;
import de.fraunhofer.iais.eis.ids.component.core.SelfDescriptionProvider;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import de.fraunhofer.iais.eis.util.Util;
import ids.messaging.core.config.ConfigContainer;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static de.fraunhofer.iais.eis.util.Util.asList;

public class BrokerSelfDescription implements SelfDescriptionProvider  {

  /**
   * The current connector configuration.
   */
  private final @NonNull ConfigContainer configContainer;

  private String catalogURI;

  public BrokerSelfDescription(@NonNull ConfigContainer configContainer, String catalogURI) {
    this.configContainer = configContainer;
    this.catalogURI=catalogURI;
  }

  @Override
  public InfrastructureComponent getSelfDescription() throws InfomodelFormalException {
    ResourceCatalog catalog = new ResourceCatalogBuilder()
        ._offeredResource_(createResourcesList())
        .build();


    Broker broker = (Broker) configContainer.getConnector();
    broker.setResourceCatalog(List.of(catalog));
    broker.setHasEndpoint(asList(new ConnectorEndpointBuilder()
        ._path_("/api/ids/data")
        ._accessURL_(configContainer.getConnector().getHasDefaultEndpoint().getAccessURL())
        ._endpointInformation_(asList(
            new TypedLiteral("This endpoint provides IDS Connector and IDS Resource registration and search capabilities at the IDS Metadata Broker.","en"),
            new TypedLiteral("Este endpoint disponibiliza funcionalidades de registo e procura de Connectores IDS e Recursos IDS no IDS Metadata Broker.", "pt")
        ))
        .build()));
    return broker;
  }

  private ArrayList<Resource> createResourcesList() {
    Artifact catalog = new ArtifactBuilder(URI.create(catalogURI)).build();
    Representation catalogRepresentation = new DataRepresentationBuilder()
        ._instance_(asList(catalog))
        .build();
    DataResource resource = new DataResourceBuilder()
        ._representation_(asList(catalogRepresentation))
        .build();
    return asList(resource);
  }

  public Connector getConnector(){
    return configContainer.getConnector();
  }

  @Override
  public String toString() {
    return getSelfDescription().toRdf();
  }
}
