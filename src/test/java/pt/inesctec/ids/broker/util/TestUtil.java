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

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import ids.messaging.core.daps.ConnectorMissingCertExtensionException;
import ids.messaging.core.daps.DapsConnectionException;
import ids.messaging.core.daps.DapsEmptyResponseException;
import ids.messaging.core.daps.DapsTokenProvider;
import ids.messaging.util.IdsMessageUtils;
import lombok.NoArgsConstructor;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;
import pt.inesctec.ids.broker.converter.XMLGregorianCalendarConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.JenaService;
import pt.inesctec.ids.broker.service.ModelService;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigInteger;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class TestUtil {

  public Connector createConnector(){
    return new BaseConnectorBuilder(URI.create("https://test-broker.com/id/test/connector-producer"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/connectorEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/test/connector/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            ._curator_(URI.create("https://www.inesctec.pt/"))
            ._description_(new TypedLiteral("Description"))
            ._title_(new TypedLiteral("Title"))
            ._publicKey_(new PublicKeyBuilder(URI.create("https://test-broker.com/id/test/publicKey/dhiasdcna"))
                    ._keyValue_("KeyValueExample".getBytes())
                    ._keyType_(KeyType.RSA)
                    .build())
            ._resourceCatalog_(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/catalog/api/connector-producer"))
                    .build())
            //._physicalLocation_( new GeoPointBuilder(URI.create("https://test/PT"))._latitude_(785)._longitude_(55).build())
            .build();
  }

  public Connector createConnectorWithMultipleCatalogs(){

    return new BaseConnectorBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/connector-producer"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/connectorEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/test/multipleCatalogs/connector/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            ._curator_(URI.create("https://www.inesctec.pt/"))
            ._description_(new TypedLiteral("Description"))
            ._title_(new TypedLiteral("Title"))
            ._publicKey_(new PublicKeyBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/publicKey/dferstgse"))
                    ._keyValue_("KeyValueExample".getBytes())
                    ._keyType_(KeyType.RSA)
                    .build())
            ._resourceCatalog_(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer")).build())
            ._resourceCatalog_(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/multipleCatalogs/catalog/2/api/connector-producer")).build())
            .build();
  }

  public Connector createConnectorSubcontracted(){
    return new BaseConnectorBuilder(URI.create("https://test-broker.com/id/test/connector-subcontracted"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/connectorEndpoint/gtedcsd"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/test/subcontracted/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            ._curator_(URI.create("https://www.inesctec.pt/"))
            ._description_(new TypedLiteral("Description"))
            ._title_(new TypedLiteral("Title"))
            ._publicKey_(new PublicKeyBuilder(URI.create("https://test-broker.com/id/test/publicKey/gtedcsd"))
                    ._keyValue_("KeyValueExample".getBytes())
                    ._keyType_(KeyType.RSA)
                    .build())
            ._resourceCatalog_(new ResourceCatalogBuilder(URI.create("https://test-broker.com/id/test/catalog/api/connector-subcontracted")).build())
            .build();
  }

  public Connector createConnectorWithoutResourceCatalog(){
    return new BaseConnectorBuilder(URI.create("https://test-broker.com/id/test/connector-producer"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/connectorEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/test/connector/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            ._curator_(URI.create("https://www.inesctec.pt/"))
            ._description_(new TypedLiteral("Description"))
            ._title_(new TypedLiteral("Title"))
            ._publicKey_(new PublicKeyBuilder(URI.create("https://test-broker.com/id/test/publicKey/dhiasdcna"))
                    ._keyValue_("KeyValueExample".getBytes())
                    ._keyType_(KeyType.RSA)
                    .build())
            .build();
  }

  public Resource createResource(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));

    LocalDateTime endDate = LocalDateTime.of(
            LocalDate.of(2025, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    XMLGregorianCalendar lastValidDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(endDate);


    Resource resource=new ResourceBuilder(URI.create("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5"))
            ._language_(Language.EN)
            ._version_("1")
            ._description_(new TypedLiteral("Information about product P20230303"))
            ._keyword_(new TypedLiteral("term1"))
            ._title_(new TypedLiteral("PDP-P20230303"))
            ._resourceEndpoint_(new ConnectorEndpointBuilder(URI.create("https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a"))
                    ._endpointDocumentation_(URI.create("https://url.for.documentation"))
                    ._accessURL_(URI.create("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5"))
                    .build())
            ._contractOffer_( new ContractOfferBuilder(URI.create("https://localhost:8080/localconnector/api/contracts/30481515-3fe0-459e-a08b-57cf17204d0f"))
                    ._permission_(new PermissionBuilder(URI.create("https://localhost:8080/localconnector/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7"))
                            ._description_(new TypedLiteral("PROVIDE_ACCESS"))
                            ._title_(new TypedLiteral("PROVIDE_ACCESS"))
                            ._action_(Action.USE)
                            .build())
                    ._contractStart_(createdDate)
                    ._contractEnd_(lastValidDate)
                    ._contractDate_(createdDate)
                    .build())
            ._representation_(
                    new RepresentationBuilder(URI.create("https://localhost:8080/localconnector/api/representations/ab2a6919-c7da-4df2-9a2a-9a508beea249"))
                            ._instance_(new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a"))
                                    ._fileName_("test-file")
                                    ._checkSum_("0")
                                    ._creationDate_(createdDate)
                                    ._byteSize_(BigInteger.valueOf(0))
                                    .build())
                            ._language_(Language.EN)
                            ._mediaType_(
                                    new IANAMediaTypeBuilder()._filenameExtension_("application/octet-stream")
                                            .build())
                            ._created_(createdDate)
                            ._modified_(createdDate).build()
            )
            ._created_(createdDate)
            ._modified_(createdDate)
            .build();
    return resource;
  }

  public Resource createResource_WithPublisherAndSovereign(){
    Resource resource=this.createResource();
    resource.setPublisher(URI.create("https://inesctec/is/publisher"));
    resource.setSovereign(URI.create("https://inesctec-producer/is/owner"));
    return resource;
  }

  public Resource createResourceWithMultipleArtefacts(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));

    LocalDateTime endDate = LocalDateTime.of(
            LocalDate.of(2025, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    XMLGregorianCalendar lastValidDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(endDate);


    Resource resource=new ResourceBuilder(URI.create("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5"))
            ._language_(Language.EN)
            ._version_("1")
            ._description_(new TypedLiteral("Information about product P20230303"))
            ._keyword_(new TypedLiteral("term1"))
            ._title_(new TypedLiteral("PDP-P20230303"))
            ._resourceEndpoint_(new ConnectorEndpointBuilder(URI.create("https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a2"))
                    ._endpointDocumentation_(URI.create("https://url.for.documentation"))
                    ._accessURL_(URI.create("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5"))
                    .build())
            ._contractOffer_( new ContractOfferBuilder(URI.create("https://localhost:8080/localconnector/api/contracts/30481515-3fe0-459e-a08b-57cf17204d0f"))
                    ._permission_(new PermissionBuilder(URI.create("https://localhost:8080/localconnector/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7"))
                            ._description_(new TypedLiteral("PROVIDE_ACCESS"))
                            ._title_(new TypedLiteral("PROVIDE_ACCESS"))
                            ._action_(Action.USE)
                            .build())
                    ._contractStart_(createdDate)
                    ._contractEnd_(lastValidDate)
                    ._contractDate_(createdDate)
                    .build())
            ._representation_(
                    new RepresentationBuilder(URI.create("https://localhost:8080/localconnector/api/representations/ab2a6919-c7da-4df2-9a2a-9a508beea249"))
                            ._instance_(List.of(
                                    new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a"))
                                      ._fileName_("test-file")
                                      ._checkSum_("0")
                                      ._creationDate_(createdDate)
                                      ._byteSize_(BigInteger.valueOf(0))
                                      .build(),
                                    new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/cduerfha9s-e1af-46a1-9f42-ndkfhcne8endaj"))
                                            ._fileName_("file2")
                                            ._checkSum_("0")
                                            ._creationDate_(createdDate)
                                            ._byteSize_(BigInteger.valueOf(2))
                                            .build(),
                                    new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/xdweudnja-e1af-46a1-9f42-384hnd"))
                                            ._fileName_("file3")
                                            ._checkSum_("0")
                                            ._creationDate_(createdDate)
                                            ._byteSize_(BigInteger.valueOf(1))
                                            .build(),
                                    new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/dweytgw76da-e1af-46a1-9f42-cnasoe0"))
                                            ._fileName_("file4")
                                            ._checkSum_("0")
                                            ._creationDate_(createdDate)
                                            ._byteSize_(BigInteger.valueOf(4))
                                            .build()))
                            ._language_(Language.EN)
                            ._mediaType_(
                                    new IANAMediaTypeBuilder(URI.create("https://localhost:8080/iANAMediaType/cnasoe0"))._filenameExtension_("application/octet-stream")
                                            .build())
                            ._created_(createdDate)
                            ._modified_(createdDate).build()
            )
            ._created_(createdDate)
            ._modified_(createdDate)
            .build();
    return resource;
  }

  public Resource createResource2(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));

    LocalDateTime endDate = LocalDateTime.of(
            LocalDate.of(2025, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    XMLGregorianCalendar lastValidDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(endDate);


    Resource resource=new ResourceBuilder(URI.create("https://localhost:8080/localconnector/api/offers/resource01"))
            ._language_(Language.EN)
            ._version_("1")
            ._description_(new TypedLiteral("Information about product P20230309"))
            ._keyword_(new TypedLiteral("term1"))
            ._title_(new TypedLiteral("PDP-P20230309"))
            ._resourceEndpoint_(new ConnectorEndpointBuilder(URI.create("https://w3id.org/idsa/autogen/connectorEndpoint/resource01"))
                    ._endpointDocumentation_(URI.create("https://url.for.documentation"))
                    ._accessURL_(URI.create("https://localhost:8080/localconnector/api/offers/resource01"))
                    .build())
            ._contractOffer_( new ContractOfferBuilder(URI.create("https://localhost:8080/localconnector/api/contracts/resource01"))
                    ._permission_(new PermissionBuilder(URI.create("https://localhost:8080/localconnector/api/rules/resource01"))
                            ._description_(new TypedLiteral("PROVIDE_ACCESS"))
                            ._title_(new TypedLiteral("PROVIDE_ACCESS"))
                            ._action_(Action.USE)
                            .build())
                    ._contractStart_(createdDate)
                    ._contractEnd_(lastValidDate)
                    ._contractDate_(createdDate)
                    .build())
            ._representation_(
                    new RepresentationBuilder(URI.create("https://localhost:8080/localconnector/api/representations/resource01"))
                            ._instance_(new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/resource01"))
                                    ._fileName_("test-file")
                                    ._checkSum_("0")
                                    ._creationDate_(createdDate)
                                    ._byteSize_(BigInteger.valueOf(0))
                                    .build())
                            ._language_(Language.EN)
                            ._mediaType_(
                                    new IANAMediaTypeBuilder()._filenameExtension_("application/octet-stream")
                                            .build())
                            ._created_(createdDate)
                            ._modified_(createdDate).build()
            )
            ._created_(createdDate)
            ._modified_(createdDate)
            .build();
    return resource;
  }

  public Resource createResourceWithIncompleteDataModel(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));

    LocalDateTime endDate = LocalDateTime.of(
            LocalDate.of(2025, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    XMLGregorianCalendar lastValidDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(endDate);


    Resource resource=new ResourceBuilder(URI.create("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5"))
            ._language_(Language.EN)
            ._version_("1")
            ._description_(new TypedLiteral("Information about product P20230303"))
            ._keyword_(new TypedLiteral("term1"))
            ._title_(new TypedLiteral("PDP-P20230303"))
            ._resourceEndpoint_(new ConnectorEndpointBuilder(URI.create("https://w3id.org/idsa/autogen/connectorEndpoint/cf5d1067-885d-438b-b85c-0cabb5a03e5a"))
                    ._endpointDocumentation_(URI.create("https://url.for.documentation"))
                    ._accessURL_(URI.create("https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5"))
                    .build())
            ._created_(createdDate)
            ._modified_(createdDate)
            .build();
    return resource;
  }

  public Permission createPermission(){
   return new PermissionBuilder(URI.create("https://localhost:8080/localconnector/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7"))
           ._description_(new TypedLiteral("PROVIDE_ACCESS"))
           ._title_(new TypedLiteral("PROVIDE_ACCESS"))
           ._action_(Action.USE)
           .build();
  }

  public Artifact createArtefact(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));

    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    return new ArtifactBuilder(URI.create("https://localhost:8080/localconnector/api/artifacts/e8e40483-e1af-46a1-9f42-15d21ac4e76a"))
            ._fileName_("test-file")
            ._checkSum_("0")
            ._creationDate_(createdDate)
            ._byteSize_(BigInteger.valueOf(0))
            .build();
  }

  public Representation createRepresentation(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    return new RepresentationBuilder(URI.create("https://localhost:8080/localconnector/api/representations/ab2a6919-c7da-4df2-9a2a-9a508beea249"))
            ._language_(Language.EN)
            ._mediaType_(
                    new IANAMediaTypeBuilder()._filenameExtension_("application/octet-stream")
                            .build())
            ._created_(createdDate)
            //._instance_(createArtefact())
            ._modified_(createdDate).build();
  }

  public ContractOffer createContract(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
            LocalDate.of(2022, 03, 23),
            LocalTime.of(12, 23,22));

    LocalDateTime endDate = LocalDateTime.of(
            LocalDate.of(2025, 03, 23),
            LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    XMLGregorianCalendar lastValidDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(endDate);
    return new ContractOfferBuilder(URI.create("https://localhost:8080/localconnector/api/contracts/30481515-3fe0-459e-a08b-57cf17204d0f"))
        ._permission_(new PermissionBuilder(URI.create("https://localhost:8080/localconnector/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7"))
            ._description_(new TypedLiteral("PROVIDE_ACCESS"))
            ._title_(new TypedLiteral("PROVIDE_ACCESS"))
            ._action_(Action.USE)
            .build())
            ._contractStart_(createdDate)
            ._contractEnd_(lastValidDate)
            ._contractDate_(createdDate)
            .build();
  }

  public ContractOffer createIncompleteContract(){
    XMLGregorianCalendarConverter xmlGregorianCalendarConverter=new XMLGregorianCalendarConverter();
    LocalDateTime startDate = LocalDateTime.of(
        LocalDate.of(2022, 03, 23),
        LocalTime.of(12, 23,22));

    LocalDateTime endDate = LocalDateTime.of(
        LocalDate.of(2025, 03, 23),
        LocalTime.of(12, 23,22));
    XMLGregorianCalendar createdDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(startDate);
    XMLGregorianCalendar lastValidDate=xmlGregorianCalendarConverter.convertLocalDateTime2XMLGregorianCalendar(endDate);
    return new ContractOfferBuilder(URI.create("https://localhost:8080/localconnector/api/contracts/30481515-3fe0-459e-a08b-57cf17204d0f"))
        ._contractStart_(createdDate)
        ._contractEnd_(lastValidDate)
        ._contractDate_(createdDate)
        .build();
  }

  public static String replaceSeparator(String string){
    return string.replaceAll("(.{64})", "$1\n")
            .replaceAll("\n", "")
            .replaceAll(System.lineSeparator(), "");
  }


  /**
   * Normalizes the LF and CRLF files
   * */
  public static String normalize(String val) {
    return val.replace("\r\n", "\n")
            .replace("\r", "\n");
  }

  public static String replaceBlankSpacesWithOneSpace(String string){
    return string.replaceAll("\\s{2,}", " ").trim();
  }

  public void removeAdminGraph(FusekiConnectionAccessor accessor){
    JenaService jenaService=new JenaService(accessor);
    jenaService.removeGraph(accessor.getAdminGraphName());
    //accessor.connect().delete(accessor.getAdminGraphName());
  }

  
  public void removeAllGraphsFromDB(FusekiConnectionAccessor accessor){
    String adminGraphName=accessor.getAdminGraphName();
    // This method is diferent from the one on ModelService, because it also removed the graphs used to test
    // I'm using, for instance, 'https://inesctec.pt/unit-tests/03'. The original mehod only removed graphs
    // that use the 'connector' keyword
    ModelService modelService=new ModelService(accessor);
    AdminGraphService adminGraphService=new AdminGraphService(accessor);
    JenaService jenaService=new JenaService(accessor);

    modelService.removeAllData();


    // Gets the graphs that do not have 'connectors' on its name
    ModelDto adminGraph=adminGraphService.getGraph();
    List statementToKeep=adminGraph.getModel()
            .listStatements(ResourceFactory.createResource(adminGraphName),
                    null, (RDFNode) null)
            .toList();
    List statementToDelete=adminGraph.getModel()
            .listStatements()
            .toList();
    // Removes and Updates the Connectors from the Admin Graph
    FusekiUtil.removeModel(adminGraph, statementToDelete);
    FusekiUtil.addModel(adminGraph, statementToKeep);
    jenaService.updateModel(adminGraph);

    //adminGraphService.getGraph(accessor).getModel().listStatements().forEach(System.out::println);
  }

  public Query createQueryToSearchByTitle(){
    return QueryFactory.create("PREFIX ids: <https://w3id.org/idsa/core/>" +
            "SELECT  ?subject ?predicate ?object\n"+
            "WHERE { " +
            "             ?subject a ids:Resource. " +
            "             ?subject ?predicate ?object " +
            "             FILTER (regex(str(?predicate), 'title','i')) \n" +
            "}");
  }

  public ResourceUpdateMessage resourceUpdateMessage(Connector conn, Resource resource, DynamicAttributeToken dat ){
    return new ResourceUpdateMessageBuilder()
        ._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(conn.getId())
        ._modelVersion_(conn.getOutboundModelVersion())
        ._senderAgent_(conn.getId())
        ._affectedResource_(resource.getId())
        .build();
  }

  public Broker broker(){
    return new BrokerBuilder(URI.create("https://test-broker.com/id/mediater/broker"))
        ._hasDefaultEndpoint_(
            new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/brokerEndpoint/dhiasdcna"))
                ._accessURL_(URI.create("https://host.docker.internal:8080/broker-01/api/ids/data")).build())
        ._inboundModelVersion_("4.2.6")
        ._outboundModelVersion_("4.2.6")
        ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
        ._maintainer_(URI.create("https://www.inesctec.pt/"))
        .build();
  }

  public DescriptionRequestMessage descriptionRequestMessage(DynamicAttributeToken dat ){
    return new DescriptionRequestMessageBuilder(URI.create("http://description/request/message/id/1a421b8c-3407-44a8-aeb9-253f145c869a"))
        ._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(URI.create("https://issuerConnector"))
        ._modelVersion_("4.0.2")
        ._senderAgent_(URI.create("https://senderAgent"))
        .build();
  }

  public ConnectorUpdateMessage connectorUpdateMessage(DynamicAttributeToken dat, Connector connector ){
    return new ConnectorUpdateMessageBuilder(URI.create("http://connector/update/message/id/1a421b8c-3407-44a8-aeb9-253f145c869a"))
        ._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_("4.1.0")
        ._senderAgent_(URI.create("https://senderAgent"))
        ._affectedConnector_(connector.getId())
        .build();
  }

  public ConnectorUnavailableMessage ConnectorUnavailableMessage(DynamicAttributeToken dat, Connector connector ){
    return new ConnectorUnavailableMessageBuilder(URI.create("http://connector/update/message/id/1a421b8c-3407-44a8-aeb9-253f145c869a"))
        ._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connector.getId())
        ._modelVersion_("4.1.0")
        ._senderAgent_(URI.create("https://senderAgent"))
        ._affectedConnector_(connector.getId())
        .build();
  }

  public ResourceUnavailableMessage resourceUnavailableMessage(Connector conn, Resource resource, DynamicAttributeToken dat ){
    return new ResourceUnavailableMessageBuilder()
        ._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(conn.getId())
        ._modelVersion_(conn.getOutboundModelVersion())
        ._senderAgent_(conn.getId())
        ._affectedResource_(resource.getId())
        .build();
  }


}
