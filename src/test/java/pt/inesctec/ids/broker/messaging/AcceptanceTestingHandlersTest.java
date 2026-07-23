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
package pt.inesctec.ids.broker.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import ids.messaging.common.SerializeException;
import ids.messaging.core.config.ConfigContainer;
import ids.messaging.core.daps.*;
import ids.messaging.handler.message.MessageHandlerException;
import ids.messaging.handler.message.MessagePayload;
import ids.messaging.handler.message.MessagePayloadInputstream;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.converter.FusekiConverter;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.FusekiUtil;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties")
class AcceptanceTestingHandlersTest {
  private BrokerService brokerService;
  private ModelService modelService;
  private AdminGraphService adminGraphService;
  @Autowired
  private FusekiConnectionAccessor accessor;

  MessageProcessedNotificationMessage messageProcessedNotification;

  Map<String, Object> map;
  Serializer serializer;
  TestUtil testUtil=new TestUtil();

  @Autowired
  private ConnectorUpdateMessageHandler connectorUpdateMessageHandler;

  @Autowired
  private ResourceUpdateMessageHandler resourceUpdateMessageHandler;

  @Autowired
  private ResourceUnavailableMessageHandler resourceUnavailableMessageHandler;

  @Autowired
  private ConnectorUnavailableMessageHandler connectorUnavailableMessageHandler;


  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  private ConnectorUpdateMessage connectorUpdateMessage;

  Connector conn;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    brokerService=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    adminGraphService=new AdminGraphService(accessor);
    conn=testUtil.createConnector();

    Broker broker=new BrokerBuilder(URI.create("https://test-broker.com/id/mediater/broker"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/brokerEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/broker-01/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            .build();

    DynamicAttributeToken dat=new DynamicAttributeTokenBuilder()._tokenValue_("Fake token")._tokenFormat_(TokenFormat.JWT).build();

    Mockito.when(connectorConfig.getConnector()).thenReturn(broker);
    Mockito.when(connectorConfig.getConfigurationModel()).thenReturn(configurationModel);
    Mockito.when(connectorConfig.getConfigurationModel().getConnectorDeployMode()).thenReturn(ConnectorDeployMode.TEST_DEPLOYMENT);
    Mockito.when(provider.provideDapsToken()).thenReturn("Mock Token");
    Mockito.when(dapsValidator.checkDat(dat)).thenReturn(true);
    Mockito.when(provider.getDAT()).thenReturn(dat);

    /*System.out.println(connectorConfig.getConnector() + " "+ connectorConfig.getConfigurationModel().getConnectorDeployMode());
    System.out.println("Provider: "+provider.getDAT() + " - " +provider.provideDapsToken());*/
    connectorUpdateMessageHandler=new ConnectorUpdateMessageHandler(connectorConfig, provider, brokerService);
    resourceUpdateMessageHandler=new ResourceUpdateMessageHandler(connectorConfig, provider, brokerService);
    resourceUnavailableMessageHandler=new ResourceUnavailableMessageHandler(connectorConfig, provider, brokerService);
    connectorUnavailableMessageHandler=new ConnectorUnavailableMessageHandler(connectorConfig, provider, brokerService);

    serializer=new Serializer();
  }

  @Test
  @Order(1)
  void connectorUpdateMessage() {
    testUtil.removeAllGraphsFromDB(accessor);

    try {

      // Create the Header to send on the handleMessage()
      connectorUpdateMessage=new ConnectorUpdateMessageBuilder()
              ._securityToken_(provider.getDAT())
              //._correlationMessage_(message.getId())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(connectorConfig.getConnector().getId())
              ._modelVersion_(this.connectorConfig.getConnector().getOutboundModelVersion())
              ._senderAgent_(connectorConfig.getConnector().getId())
              ._affectedConnector_(URI.create("https://test-broker.com/id/test/connector"))
              //._recipientAgent_(message.getRecipientAgent())
              .build();

      // Create the Payload to send on the handleMessage()
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(conn.toString()), new ObjectMapper());

      // Send the Message
      MessageResponse messageResponse=
              connectorUpdateMessageHandler
                      .handleMessage((ConnectorUpdateMessageImpl) connectorUpdateMessage,
              messagePayload);

      // Get the Response
      map=messageResponse.createMultipartMap(serializer);

      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }

      try {
        Connector payload = serializer.deserialize(map.get("payload").toString(), Connector.class);

        // Check if Connector was inserted
        // Get Connector Model
        Optional<ModelDto> connectorModel=modelService
                .getModelWithConnectorIDEqualTo(payload.getId());
        assertTrue(connectorModel.isPresent());
      }catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(Connector.class, "Error while deserializing payload", "Method should return a Connector.");
      }

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    } catch (ConnectorMissingCertExtensionException e) {
      throw new RuntimeException(e);
    } catch (DapsConnectionException e) {
      throw new RuntimeException(e);
    } catch (DapsEmptyResponseException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(2)
  void ResourceUpdateMessage() {
    try {
      Resource resource=testUtil.createResource();


      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUpdateMessage message=new ResourceUpdateMessageBuilder()._securityToken_(provider.getDAT())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(conn.getId())
              ._modelVersion_(conn.getOutboundModelVersion())
              ._senderAgent_(conn.getId())
              ._affectedResource_(resource.getId())
              .build();

      MessageResponse messageResponse=resourceUpdateMessageHandler
              .handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
     /* System.out.println(map.get("header"));
      System.out.println(map.get("payload"));*/
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }

      try {
        final var payload = serializer.deserialize((String) map.get("payload").toString(), Resource.class);
        // Creates the List that should be returned
        List<Statement> catalogStatementsResult=createResultList("http://localhost:3032/connectors/connector-producer/-846787637/-281147133",
                conn.getResourceCatalog().get(0).getId().toString(),
                "http://localhost:3032/connectors/connector-producer/-846787637/-281147133");

        // Get the Connector Model
        Optional<ModelDto> model=modelService.getModelWithConnectorIDEqualTo(conn.getId());
        assertTrue(model.isPresent(), "Connector needs to exist on the Database");

        // Get the Catalog internal id
        Statement catalogID=model.get().mapInternalIDsWithIDs(conn.getResourceCatalog().stream().findFirst().get().getId()).get(0);

        // Get list of Statements belonging to the Catalog (Catalog sub-model)
        List<Statement> catalogStatementsBD= model.get().listStatementsWithSubjectEqualTo(
                URI.create(catalogID.getSubject().toString()));
        catalogStatementsBD.forEach(System.out::println);

      }
      catch (IOException e){
        throw new RuntimeException(e);
      }

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException |
             SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(3)
  void ResourceUnavailabeMessage() {

    try {
      Resource resource=testUtil.createResource();


      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUnavailableMessage message=new ResourceUnavailableMessageBuilder()._securityToken_(provider.getDAT())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(conn.getId())
              ._modelVersion_(conn.getOutboundModelVersion())
              ._senderAgent_(conn.getId())
              ._affectedResource_(resource.getId())
              .build();

      MessageResponse messageResponse=resourceUnavailableMessageHandler
              .handleMessage((ResourceUnavailableMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      /*System.out.println(map.get("header"));*/

      ModelDto modelBeforeExchange=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get();

      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }

      FusekiConverter converter=new FusekiConverter();
      // Checks if the BaseConnector statements are still on the DB

      System.out.println(converter.modelToTurleFormat(modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel()));

      // Remove Resource. This returns a Model without the Resource
      Model modelWithoutResource=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel();
      //System.err.println(converter.modelToTurleFormat(modelWithoutResource));
      System.out.println(FusekiUtil.getStatementsDiff(modelBeforeExchange.getModel(), modelWithoutResource));
      // Sees if the Model after removing the resource is the same as before adding the resource
      assertFalse(FusekiUtil.getStatementsDiff(modelBeforeExchange.getModel(), modelWithoutResource).hasDiff());

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException |
             SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(4)
  void ConnectorUnavailableMessage() {

    try {

      // Create the Header to send on the handleMessage()
      ConnectorUnavailableMessage connectorUnavailableMessageHeader=new ConnectorUnavailableMessageBuilder()
              ._securityToken_(provider.getDAT())
              //._correlationMessage_(message.getId())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(conn.getId())
              ._modelVersion_(this.connectorConfig.getConnector().getOutboundModelVersion())
              ._senderAgent_(conn.getId())
              ._affectedConnector_(conn.getId())
              //._recipientAgent_(message.getRecipientAgent())
              .build();

      // Create the Payload to send on the handleMessage()
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(conn.toString()), new ObjectMapper());

      // Send the Message
      MessageResponse messageResponse=connectorUnavailableMessageHandler
              .handleMessage((ConnectorUnavailableMessageImpl) connectorUnavailableMessageHeader,messagePayload);

      // Get the Response
      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }

      // Get GraphName
      Optional<String> graphName=modelService
              .convertConnectorIDToGraphName(conn.getId());

      // GET Admin Graph
      ModelDto adminGraph= adminGraphService.getGraph();
      //Check if the Connector is inactive
      assertEquals(
              adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName.get())).stream().findFirst().get().getObject().asLiteral().getBoolean(),
              false);

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException | MessageHandlerException | ConnectorMissingCertExtensionException |
             DapsConnectionException | DapsEmptyResponseException e) {
      throw new RuntimeException(e);
    }
  }

  /** Creates the Statements (in String format) that belong to the Catalog */
  private List<Statement> createResultList(String catalogInternalID, String catalogID, String resourceInternalID){
    List<Statement> catalogStatementsResult=new ArrayList<>();
    // Add the Resource Statement
    catalogStatementsResult.add(ResourceFactory.createStatement(
            ResourceFactory.createResource(catalogInternalID), //Subject: The Catalog Subject URI
            ResourceFactory.createProperty("https://w3id.org/idsa/core/offeredResource"), //Predicate:  ids:offeredResource
            ResourceFactory.createResource(String.valueOf(resourceInternalID)))); // Object: the resource offer URI

    // Add the Catalog SameAs Statement
    catalogStatementsResult.add(ResourceFactory.createStatement(
            ResourceFactory.createResource(catalogInternalID), //Subject: The Catalog Subject URI
            ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#sameAs"), //Predicate:  owl:sameAs
            ResourceFactory.createResource(catalogID))); // Object: the resource offer URI

    // Add the Resource Type Statement (Statement created automatically)
    catalogStatementsResult.add(ResourceFactory.createStatement(
            ResourceFactory.createResource(catalogInternalID), //Subject: The Catalog Subject URI
            ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), //Predicate:  type
            ResourceFactory.createResource("https://w3id.org/idsa/core/ResourceCatalog")));

    return catalogStatementsResult;
  }


}