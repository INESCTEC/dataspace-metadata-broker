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
import ids.messaging.handler.message.MessagePayload;
import ids.messaging.handler.message.MessagePayloadInputstream;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ResourceUpdateMessageHandlerTest {
  private BrokerService brokerService;
  private ModelService modelService;
  @Autowired
  private FusekiConnectionAccessor accessor;

  MessageProcessedNotificationMessage messageProcessedNotification;

  Map<String, Object> map;
  Serializer serializer;
  TestUtil testUtil=new TestUtil();
  Broker broker;
  DynamicAttributeToken dat;

  @Autowired
  private ResourceUpdateMessageHandler handler;

  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    brokerService=new BrokerService(accessor);
    modelService=new ModelService(accessor);

    broker=testUtil.broker();

    DynamicAttributeToken dat_aux=new DynamicAttributeTokenBuilder()._tokenValue_("Fake token")._tokenFormat_(TokenFormat.JWT).build();

    Mockito.when(connectorConfig.getConnector()).thenReturn(broker);
    Mockito.when(connectorConfig.getConfigurationModel()).thenReturn(configurationModel);
    Mockito.when(connectorConfig.getConfigurationModel().getConnectorDeployMode()).thenReturn(ConnectorDeployMode.TEST_DEPLOYMENT);
    Mockito.when(provider.provideDapsToken()).thenReturn("Mock Token");
    Mockito.when(dapsValidator.checkDat(dat_aux)).thenReturn(true);
    Mockito.when(provider.getDAT()).thenReturn(dat_aux);

    /*System.out.println(connectorConfig.getConnector() + " "+ connectorConfig.getConfigurationModel().getConnectorDeployMode());
    System.out.println("Provider: "+provider.getDAT() + " - " +provider.provideDapsToken());*/
    handler=new ResourceUpdateMessageHandler(connectorConfig, provider, brokerService);

    serializer=new Serializer();

    testUtil.removeAllGraphsFromDB(accessor);

    try {
      dat=provider.getDAT();
    }catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(1)
  void handleMessage() {
    try {
      Resource resource=testUtil.createResource();
      Connector conn=testUtil.createConnector();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUpdateMessage message=new ResourceUpdateMessageBuilder()._securityToken_(provider.getDAT())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(conn.getId())
              ._modelVersion_(conn.getOutboundModelVersion())
              ._senderAgent_(conn.getId())
              ._affectedResource_(resource.getId())
              .build();

      MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("header"));
      /*System.out.println(map.get("payload"));*/
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing message header", "Method should return a MessageProcessedNotificationMessage.");
      }

      try {
        final var payload = serializer.deserialize((String) map.get("payload").toString(), Resource.class);
        System.out.println("Catalog!!!!!");
        conn.getResourceCatalog().forEach(System.out::println);

        System.out.println("Payload!!!!!");
        System.out.println(payload);

      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(Resource.class, "Error while deserializing message payload", "Method should return a Resource.");
      }

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException |
             SerializeException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Tests adding a Resource when the Connector does not hava any Catalog
   * @parameter connector without a resource catalog
   * @return an error message that says "Catalog of connector CONNECTOR not found. Did you send a ConnectorUpdateMessage yet?"
   * */
  @Test
  @Order(2)
  void addResourceWithoutCatalog() {
    try {
      Resource resource=testUtil.createResource();
      Connector conn=testUtil.createConnectorWithoutResourceCatalog();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUpdateMessage message=testUtil.resourceUpdateMessage(conn, resource, dat);

      MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      //System.out.println(map.get("payload"));

      System.out.println(map.get("header").toString());
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);
        assertInstanceOf(RejectionMessage.class, requestHeader);
        Assertions.assertTrue(RejectionMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return RejectionMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);
        assertThat(requestHeader.getTransferContract()).isNull();

        assertThat(map.get("payload")).isEqualTo("Catalog of connector https://test-broker.com/id/test/connector-producer not found. Did you send a ConnectorUpdateMessage yet?");
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing message header", "Method should return a MessageProcessedNotificationMessage.");
      }

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(3)
  void addResourceWith1Catalog() {
    try {
      Resource resource=testUtil.createResource();
      Connector conn=testUtil.createConnector();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUpdateMessage message=testUtil.resourceUpdateMessage(conn, resource, dat);

      MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      //System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing message header", "Method should return a MessageProcessedNotificationMessage.");
      }

      // Creates the List that should be returned
      List<Statement> catalogStatementsResult=createResultList("http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664",
              conn.getResourceCatalog().get(0).getId().toString(),
              "http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692");

      // Get the Connector Model
      Optional<ModelDto> model=modelService.getModelWithConnectorIDEqualTo(conn.getId());
      assertTrue(model.isPresent(), "Connector needs to exist on the Database");

      // Get the Catalog internal id
      Statement catalogID=model.get().mapInternalIDsWithIDs(conn.getResourceCatalog().stream().findFirst().get().getId()).get(0);

      // Get list of Statements belonging to the Catalog (Catalog sub-model)
      List<Statement> catalogStatementsBD= model.get().listStatementsWithSubjectEqualTo(
              URI.create(catalogID.getSubject().toString()));
      catalogStatementsBD.forEach(System.out::println);

      // Check if the Catalog Model is correct
      assertEquals(catalogStatementsBD, catalogStatementsResult);

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Tests adding an incomplete Resource
   * @parameter resource without a complete data model
   * @return an error message that says "Resource RESOURCE cannot be inserted on the Broker. It needs to have a LACKING_MODEL "
   * */
  @Test
  @Order(3)
  void addResourceWithIncompleteDataModel() throws SerializeException {


      Resource resource=testUtil.createResourceWithIncompleteDataModel();
      Connector conn=testUtil.createConnector();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUpdateMessage message=testUtil.resourceUpdateMessage(conn, resource, dat);

      MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      //System.out.println(map.get("payload"));
      try {
        System.out.println(map.get("header").toString());
        System.out.println(map.get("payload").toString());

        final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);

        assertInstanceOf(RejectionMessage.class, requestHeader);
        Assertions.assertTrue(RejectionMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return RejectionMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);
        assertThat(requestHeader.getTransferContract()).isNull();

        assertThat(map.get("payload")).isEqualTo("Resource "+ resource.getId()+" cannot be inserted on the Broker. It needs to have a Representation.");

      }

      catch (IOException e){
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

  @Test
  @Order(5)
  void addResourceWithPublisherAndSovereign() {
    try {
      Resource resource=testUtil.createResource_WithPublisherAndSovereign();
      Connector conn=testUtil.createConnector();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUpdateMessage message=testUtil.resourceUpdateMessage(conn, resource, dat);

      MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      //System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing message header", "Method should return a MessageProcessedNotificationMessage.");
      }

      // Creates the List that should be returned
      List<Statement> catalogStatementsResult=createResultList("http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664",
              conn.getResourceCatalog().get(0).getId().toString(),
              "http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692");

      // Get the Connector Model
      Optional<ModelDto> model=modelService.getModelWithConnectorIDEqualTo(conn.getId());
      assertTrue(model.isPresent(), "Connector needs to exist on the Database");

      // Get the Catalog internal id
      Statement catalogID=model.get().mapInternalIDsWithIDs(conn.getResourceCatalog().stream().findFirst().get().getId()).get(0);

      // Get list of Statements belonging to the Catalog (Catalog sub-model)
      List<Statement> catalogStatementsBD= model.get().listStatementsWithSubjectEqualTo(
              URI.create(catalogID.getSubject().toString()));
      catalogStatementsBD.forEach(System.out::println);

      // Check if the Catalog Model is correct
      assertEquals(catalogStatementsBD, catalogStatementsResult);

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Tests adding a Resource to an inactive COnnector
   * @parameter resource from an inactive Connector
   * @return an error message that says "Connector not found with ID 'CONNECTOR'. Did you send a ConnectorUpdateMessage yet?"
   * */
  @Test
  @Order(6)
  void addResourceWithInactiveConnector() throws SerializeException {


    Resource resource=testUtil.createResource();
    Connector conn=testUtil.createConnector();

    // Add Connector data
    brokerService.addConnectorToFuseki(conn);
    // Make connector inactive
    brokerService.makeConnectorUnavailable(conn.getId());

    MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

    ResourceUpdateMessage message=testUtil.resourceUpdateMessage(conn, resource, dat);

    MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

    map=messageResponse.createMultipartMap(serializer);
    //System.out.println(map.get("payload"));
    try {
      System.out.println(map.get("header").toString());
      System.out.println(map.get("payload").toString());

      final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);

      assertInstanceOf(RejectionMessage.class, requestHeader);
      Assertions.assertTrue(RejectionMessage.class.isAssignableFrom(requestHeader.getClass()),
          "Method should return RejectionMessage. It returns "+ requestHeader.getClass());
      assertThat(requestHeader.getSecurityToken()).isNotNull();
      assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
      assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
      assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
      assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
      assertThat(requestHeader.getIssued()).isNotNull();
      assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.BAD_PARAMETERS);
      assertThat(requestHeader.getTransferContract()).isNull();

      assertThat(map.get("payload")).isEqualTo("Connector not found with ID 'https://test-broker.com/id/test/connector-producer'. Did you send a ConnectorUpdateMessage yet?");

    }

    catch (IOException e){
      throw new RuntimeException(e);
    }

  }


  /**
   * Tests adding a Resource to an unknown Connector
   * @parameter resource from an unknown Connector
   * @return an error message that says "Connector not found with ID : 'CONNECTOR'."
   * */
  @Test
  @Order(7)
  void addResourceWithUnknownConnector() throws SerializeException {

    // Create the variables but do not insert on the Database
    Resource resource=testUtil.createResource();
    Connector conn=testUtil.createConnector();


    MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

    ResourceUpdateMessage message=testUtil.resourceUpdateMessage(conn, resource, dat);

    MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

    map=messageResponse.createMultipartMap(serializer);
    //System.out.println(map.get("payload"));
    try {
      System.out.println(map.get("header").toString());
      System.out.println(map.get("payload").toString());

      final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);

      assertInstanceOf(RejectionMessage.class, requestHeader);
      Assertions.assertTrue(RejectionMessage.class.isAssignableFrom(requestHeader.getClass()),
          "Method should return RejectionMessage. It returns "+ requestHeader.getClass());
      assertThat(requestHeader.getSecurityToken()).isNotNull();
      assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
      assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
      assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
      assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
      assertThat(requestHeader.getIssued()).isNotNull();
      assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.BAD_PARAMETERS);
      assertThat(requestHeader.getTransferContract()).isNull();

      assertThat(map.get("payload")).isEqualTo("Connector not found with ID : 'https://test-broker.com/id/test/connector-producer'");

    }

    catch (IOException e){
      throw new RuntimeException(e);
    }

  }

  @Test
  void postmanExample() {
    try {
      Resource resource=testUtil.createResource();
      Connector conn=testUtil.createConnector();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream("{\n" +
          "    \"@context\": {\n" +
          "        \"ids\": \"https://w3id.org/idsa/core/\",\n" +
          "        \"idsc\": \"https://w3id.org/idsa/code/\"\n" +
          "    },\n" +
          "    \"@type\": \"ids:Resource\",\n" +
          "    \"@id\": \"https://test.connector.de/myFirstResource\",\n" +
          "    \"ids:author\": [\n" +
          "        {\n" +
          "            \"@id\": \"https://w3id.org/idsa/autogen/person/8b51a76a-7316-4285-b96a-722bf7a944f8\",\n" +
          "            \"@type\": \"ids:Person\",\n" +
          "            \"ids:emailAddress\": [\n" +
          "                \"info@example.org\"\n" +
          "            ],\n" +
          "            \"ids:familyName\": \"Schmitzs\",\n" +
          "            \"ids:givenName\": \"Max\",\n" +
          "            \"ids:homepage\": \"http://example.org\",\n" +
          "            \"ids:phoneNumber\": [\n" +
          "                \"012345678891\"\n" +
          "            ]\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:contentStandard\": {\n" +
          "        \"@id\": \"http://contentstandard.org\"\n" +
          "    },\n" +
          "    \"ids:contentType\": {\n" +
          "        \"@id\": \"idsc:INTERFACE_DEFINITION\"\n" +
          "    },\n" +
          "    \"ids:contractOffer\": {\n" +
          "        \"@id\": \"https://w3id.org/idsa/autogen/contractOffer/b6d78c1f-bccb-4615-9a67-bf0ab729428a\",\n" +
          "        \"@type\": \"ids:ContractOffer\",\n" +
          "        \"ids:permission\": [\n" +
          "            {\n" +
          "                \"@type\": \"ids:Permission\",\n" +
          "                \"@id\" : \"https://w3id.org/idsa/autogen/api/rules/1b1c5f00-4fd1-4910-8483-8b78274662c7\",\n" +
          "                \"ids:action\": [\n" +
          "                    {\n" +
          "                        \"@id\": \"https://w3id.org/idsa/code/USE\",\n" +
          "                        \"@type\": \"ids:Action\"\n" +
          "                    }\n" +
          "                ],\n" +
          "                \"ids:assignee\": [\n" +
          "                    {\n" +
          "                        \"@id\": \"http://example.org/assignee\"\n" +
          "                    }\n" +
          "                ],\n" +
          "                \"ids:constraint\": [\n" +
          "                    {\n" +
          "                        \"@id\": \"https://w3id.org/idsa/autogen/constraint/4be87aa3-e203-4ac8-b4d6-ea006814d494\",\n" +
          "                        \"@type\": \"ids:Constraint\",\n" +
          "                        \"ids:leftOperand\": {\n" +
          "                            \"@id\": \"https://w3id.org/idsa/code/SECURITY_LEVEL\"\n" +
          "                        },\n" +
          "                        \"ids:operator\": {\n" +
          "                            \"@id\": \"https://w3id.org/idsa/code/EQ\"\n" +
          "                        },\n" +
          "                        \"ids:pipEndpoint\": {\n" +
          "                            \"@id\": \"https://w3id.org/idsa/autogen/pipEndpoint/mcdkmcsnrbge\",\n" +
          "                            \"@type\": \"ids:PIP\",\n" +
          "                            \"ids:endpointURI\": {\n" +
          "                                \"@id\": \"https://ids.org/EXAMPLE/realEndpointURI\"\n" +
          "                            },\n" +
          "                            \"ids:interfaceDescription\": {\n" +
          "                                \"@id\": \"https://ids.org/PIP/askIfPurpose\"\n" +
          "                            }\n" +
          "                        },\n" +
          "                        \"ids:rightOperandReference\": {\n" +
          "                            \"@id\": \"https://w3id.org/idsa/code/TRUST_SECURITY_PROFILE\"\n" +
          "                        }\n" +
          "                    }\n" +
          "                ]\n" +
          "            }\n" +
          "        ]\n" +
          "    },\n" +
          "    \"ids:created\": {\n" +
          "        \"@type\": \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\",\n" +
          "        \"@value\": \"2003-01-10T00:00:00Z\"\n" +
          "    },\n" +
          "    \"ids:customLicense\": {\n" +
          "        \"@id\": \"http://customlicense.org\"\n" +
          "    },\n" +
          "    \"ids:dataCreator\": {\n" +
          "        \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "        \"@value\": \"Data creator/Owner\"\n" +
          "    },\n" +
          "    \"ids:description\": [\n" +
          "        {\n" +
          "            \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "            \"@value\": \"Data information for all the components.\"\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:domainVocabulary\": {\n" +
          "        \"@id\": \"https://w3id.org/idsa/autogen/vocabularyData/2d98d466-52ce-48b8-a29e-936f7f4efa9e\",\n" +
          "        \"@type\": \"ids:VocabularyData\",\n" +
          "        \"ids:vocabulary\": {\n" +
          "            \"@id\": \"http://vocabulary.org\"\n" +
          "        }\n" +
          "    },\n" +
          "    \"ids:keyword\": [\n" +
          "        {\n" +
          "            \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "            \"@value\": \"data\"\n" +
          "        },\n" +
          "        {\n" +
          "            \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "            \"@value\": \"PLATOON\"\n" +
          "        },\n" +
          "        {\n" +
          "            \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "            \"@value\": \"component\"\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:language\": [\n" +
          "        {\n" +
          "            \"@id\": \"idsc:EN\"\n" +
          "        },\n" +
          "        {\n" +
          "            \"@id\": \"idsc:ES\"\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:mainTitle\": [\n" +
          "        {\n" +
          "            \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "            \"@value\": \"PLATOON components\"\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:modified\": {\n" +
          "        \"@type\": \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\",\n" +
          "        \"@value\": \"2020-01-29T00:00:00+00:00\"\n" +
          "    },\n" +
          "    \"ids:paymentModality\": {\n" +
          "        \"@id\": \"idsc:FREE\"\n" +
          "    },\n" +
          "    \"ids:rawData\": false,\n" +
          "    \"ids:representation\": [\n" +
          "        {\n" +
          "            \"@id\": \"https://w3id.org/idsa/autogen/textRepresentation/8a23edb5-be86-4722-afd4-fd2276c9bc18\",\n" +
          "            \"@type\": \"ids:TextRepresentation\",\n" +
          "            \"ids:created\": {\n" +
          "                \"@type\": \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\",\n" +
          "                \"@value\": \"2013-11-14T00:00:00+00:00\"\n" +
          "            },\n" +
          "            \"ids:domainVocabulary\": {\n" +
          "                \"@id\": \"https://w3id.org/idsa/autogen/vocabularyData/bbe50ee4-5033-4c13-bf43-35c733a7b781\",\n" +
          "                \"@type\": \"ids:VocabularyData\",\n" +
          "                \"ids:vocabulary\": {\n" +
          "                    \"@id\": \"http://textVovabulary.com\"\n" +
          "                }\n" +
          "            },\n" +
          "            \"ids:instance\": [\n" +
          "                {\n" +
          "                    \"@id\": \"https://w3id.org/idsa/autogen/artifact/aa1cf3d9-8aa1-42ef-9f85-08ce0f48005b\",\n" +
          "                    \"@type\": \"ids:Artifact\",\n" +
          "                    \"ids:byteSize\": 2678,\n" +
          "                    \"ids:creationDate\": {\n" +
          "                        \"@type\": \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\",\n" +
          "                        \"@value\": \"2020-06-23T17:27:18.599+02:00\"\n" +
          "                    },\n" +
          "                    \"ids:fileName\": \"data.pdf\"\n" +
          "                }\n" +
          "            ],\n" +
          "            \"ids:mediaType\": {\n" +
          "                \"@id\": \"idsc:APPLICATION_PDF\",\n" +
          "                \"@type\": \"ids:IANAMediaType\"\n" +
          "            },\n" +
          "            \"ids:modified\": {\n" +
          "                \"@type\": \"http://www.w3.org/2001/XMLSchema#dateTimeStamp\",\n" +
          "                \"@value\": \"2020-01-15T00:00:00+00:00\"\n" +
          "            },\n" +
          "            \"ids:representationStandard\": {\n" +
          "                \"@id\": \"http://textRepresentation.org\"\n" +
          "            }\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:researchDataType\": {\n" +
          "        \"@id\": \"idsc:OBSERVATIONAL_DATA\"\n" +
          "    },\n" +
          "    \"ids:standardLicense\": {\n" +
          "        \"@id\": \"idsc:OTHERFREEWARELICENSE\"\n" +
          "    },\n" +
          "    \"ids:title\": [\n" +
          "        {\n" +
          "            \"@type\": \"http://www.w3.org/2001/XMLSchema#string\",\n" +
          "            \"@value\": \"PLATOON Marketplace Components\"\n" +
          "        }\n" +
          "    ],\n" +
          "    \"ids:version\": \"version 3.0.0\"\n" +
          "}"), new ObjectMapper());

      ResourceUpdateMessage message=new ResourceUpdateMessageBuilder()._securityToken_(provider.getDAT())
          ._issued_(IdsMessageUtils.getGregorianNow())
          ._issuerConnector_(conn.getId())
          ._modelVersion_(conn.getOutboundModelVersion())
          ._senderAgent_(conn.getId())
          ._affectedResource_(resource.getId())
          .build();

      MessageResponse messageResponse=handler.handleMessage((ResourceUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("header"));
      /*System.out.println(map.get("payload"));*/
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
            "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing message header", "Method should return a MessageProcessedNotificationMessage.");
      }

      try {
        final var payload = serializer.deserialize((String) map.get("payload").toString(), Resource.class);
        System.out.println("Catalog!!!!!");
        conn.getResourceCatalog().forEach(System.out::println);

        System.out.println("Payload!!!!!");
        System.out.println(payload);

      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(Resource.class, "Error while deserializing message payload", "Method should return a Resource.");
      }

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException |
             SerializeException e) {
      throw new RuntimeException(e);
    }
  }

}