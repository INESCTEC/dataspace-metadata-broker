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
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.ConnectorUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.FusekiUtil;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static pt.inesctec.ids.broker.config.IDSConstants.SAME_AS;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ResourceUnavailableMessageHandlerTest {
  private BrokerService brokerService;
  private ModelService modelService;
  @Autowired
  private FusekiConnectionAccessor accessor;

  MessageProcessedNotificationMessage messageProcessedNotification;

  Map<String, Object> map;
  Serializer serializer;
  TestUtil testUtil=new TestUtil();

  @Autowired
  private ResourceUnavailableMessageHandler handler;

  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  Broker broker;
  private DynamicAttributeToken dat;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    brokerService=new BrokerService(accessor);
    modelService=new ModelService(accessor);

    broker=new BrokerBuilder(URI.create("https://test-broker.com/id/mediater/broker"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/brokerEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/broker-01/api/ids/data")).build())
            ._inboundModelVersion_("4.2.6")
            ._outboundModelVersion_("4.2.6")
            ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
            ._maintainer_(URI.create("https://www.inesctec.pt/"))
            .build();

    DynamicAttributeToken datToConfig=new DynamicAttributeTokenBuilder()._tokenValue_("Fake token")._tokenFormat_(TokenFormat.JWT).build();

    Mockito.when(connectorConfig.getConnector()).thenReturn(broker);
    Mockito.when(connectorConfig.getConfigurationModel()).thenReturn(configurationModel);
    Mockito.when(connectorConfig.getConfigurationModel().getConnectorDeployMode()).thenReturn(ConnectorDeployMode.TEST_DEPLOYMENT);
    Mockito.when(provider.provideDapsToken()).thenReturn("Mock Token");
    Mockito.when(dapsValidator.checkDat(datToConfig)).thenReturn(true);
    Mockito.when(provider.getDAT()).thenReturn(datToConfig);

    /*System.out.println(connectorConfig.getConnector() + " "+ connectorConfig.getConfigurationModel().getConnectorDeployMode());
    System.out.println("Provider: "+provider.getDAT() + " - " +provider.provideDapsToken());*/
    handler=new ResourceUnavailableMessageHandler(connectorConfig, provider, brokerService);

    serializer=new Serializer();
    dat=provider.getDAT();
  }

  @Test
  @Order(1)
  void handleMessage() {
    testUtil.removeAllGraphsFromDB(accessor);

    try {
      Resource resource=testUtil.createResource();
      Connector conn=testUtil.createConnector();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUnavailableMessage message=new ResourceUnavailableMessageBuilder()._securityToken_(provider.getDAT())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(conn.getId())
              ._modelVersion_(conn.getOutboundModelVersion())
              ._senderAgent_(conn.getId())
              ._affectedResource_(resource.getId())
              .build();

      messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
              ._securityToken_(provider.getDAT())
              ._correlationMessage_(message.getId())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(connectorConfig.getConnector().getId())
              ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
              ._senderAgent_(connectorConfig.getConnector().getId())
              .build();

      Model modelBeforeInsertingResource=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel();

      // Add Resource data
      brokerService.addResourceToFuseki(resource, conn.getId());

      MessageResponse messageResponse=handler.handleMessage((ResourceUnavailableMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      /*System.out.println(map.get("header"));*/

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
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }


      // Remove Resource. This returns a Model without the Resource
      Model modelAfterRemovingResource=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel();
      //System.err.println(converter.modelToTurleFormat(modelWithoutResource));

      System.out.println(FusekiUtil.getStatementsDiff(modelBeforeInsertingResource, modelAfterRemovingResource));
      // Sees if the Model after removing the resource is the same as before adding the resource
      assertFalse(FusekiUtil.getStatementsDiff(modelBeforeInsertingResource, modelAfterRemovingResource).hasDiff());


      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException |
             SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(1)
  void handleMessage_Vcese12Subcontracted() {
    testUtil.removeAllGraphsFromDB(accessor);

    Model connectorModel=getConnectorModel();

    List<Statement> connectorModelWithoutSameAs=connectorModel.listStatements().filterDrop(i->i.getPredicate().equals(ResourceFactory.createProperty(SAME_AS))).toList();
    FusekiConverter fusekiConverter=new FusekiConverter();
    Connector conn=ConnectorUtil.deserialize(fusekiConverter.listOfStatements2Model(connectorModelWithoutSameAs));
    System.out.println(conn.toRdf());

    try {
      Resource resource=testUtil.createResource();

      // Add Connector data
      brokerService.addConnectorToFuseki(conn);

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUnavailableMessage message=new ResourceUnavailableMessageBuilder()._securityToken_(provider.getDAT())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(conn.getId())
              ._modelVersion_(conn.getOutboundModelVersion())
              ._senderAgent_(conn.getId())
              ._affectedResource_(resource.getId())
              .build();

      messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
              ._securityToken_(provider.getDAT())
              ._correlationMessage_(message.getId())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(connectorConfig.getConnector().getId())
              ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
              ._senderAgent_(connectorConfig.getConnector().getId())
              .build();



      Model modelBeforeInsertingResource=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel();

      // Add Resource data
      brokerService.addResourceToFuseki(resource, conn.getId());

      MessageResponse messageResponse=handler.handleMessage((ResourceUnavailableMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);

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
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }


      // Remove Resource. This returns a Model without the Resource
      Model modelAfterRemovingResource=modelService.getModelWithConnectorIDEqualTo(conn.getId()).get().getModel();
      //System.err.println(converter.modelToTurleFormat(modelWithoutResource));

      System.out.println(FusekiUtil.getStatementsDiff(modelBeforeInsertingResource, modelAfterRemovingResource));
      // Sees if the Model after removing the resource is the same as before adding the resource
      assertFalse(FusekiUtil.getStatementsDiff(modelBeforeInsertingResource, modelAfterRemovingResource).hasDiff());


      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      throw new RuntimeException(e);
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void unknownConnector() {
    testUtil.removeAllGraphsFromDB(accessor);

    Resource resource=testUtil.createResource();
    Connector conn=testUtil.createConnector();

    try {
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

      ResourceUnavailableMessage message=new ResourceUnavailableMessageBuilder()._securityToken_(provider.getDAT())
          ._issued_(IdsMessageUtils.getGregorianNow())
          ._issuerConnector_(conn.getId())
          ._modelVersion_(conn.getOutboundModelVersion())
          ._senderAgent_(conn.getId())
          ._affectedResource_(resource.getId())
          .build();

      messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
          ._securityToken_(dat)
          ._correlationMessage_(message.getId())
          ._issued_(IdsMessageUtils.getGregorianNow())
          ._issuerConnector_(connectorConfig.getConnector().getId())
          ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
          ._senderAgent_(connectorConfig.getConnector().getId())
          .build();


      MessageResponse messageResponse=handler.handleMessage((ResourceUnavailableMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);

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

        assertThat(map.get("payload")).isEqualTo("Connector not found with ID : 'https://test-broker.com/id/test/connector-producer'");

      }catch (IOException e){
        throw new RuntimeException(e);
      }

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException |
             SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests removing a Resource from an inactive COnnector
   * @parameter resource from an inactive Connector
   * @return an error message that says "Connector not found with ID 'CONNECTOR'. Did you send a ConnectorUpdateMessage yet?"
   * */
  @Test
  void inactiveConnector() throws SerializeException {

    Resource resource=testUtil.createResource();
    Connector conn=testUtil.createConnector();

    // Add Connector data
    brokerService.addConnectorToFuseki(conn);
    brokerService.addResourceToFuseki(resource, conn.getId());
    // Make connector inactive
    brokerService.makeConnectorUnavailable(conn.getId());

    MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

    ResourceUnavailableMessage message=new ResourceUnavailableMessageBuilder()._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(conn.getId())
        ._modelVersion_(conn.getOutboundModelVersion())
        ._senderAgent_(conn.getId())
        ._affectedResource_(resource.getId())
        .build();

    messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
        ._securityToken_(dat)
        ._correlationMessage_(message.getId())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connectorConfig.getConnector().getId())
        ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
        ._senderAgent_(connectorConfig.getConnector().getId())
        .build();


    MessageResponse messageResponse=handler.handleMessage((ResourceUnavailableMessageImpl) message,messagePayload);

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

      assertThat(map.get("payload")).isEqualTo("Connector not found with ID 'https://test-broker.com/id/test/connector-producer'. Did you send a ConnectorUpdateMessage yet?");

    }

    catch (IOException e){
      throw new RuntimeException(e);
    }

  }

  /**
   * Tests adding a Resource to an inactive COnnector
   * @parameter resource from an inactive Connector
   * @return an error message that says "Connector not found with ID 'CONNECTOR'. Did you send a ConnectorUpdateMessage yet?"
   * */
  @Test
  void resourceDoesNotExist() throws SerializeException {

    modelService.removeAllData();

    Resource resource=testUtil.createResource();
    Connector conn=testUtil.createConnector();

    // Add Connector data
    brokerService.addConnectorToFuseki(conn);

    MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(resource.toString()), new ObjectMapper());

    ResourceUnavailableMessage message=new ResourceUnavailableMessageBuilder()
        ._securityToken_(dat)
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(conn.getId())
        ._modelVersion_(conn.getOutboundModelVersion())
        ._senderAgent_(conn.getId())
        ._affectedResource_(resource.getId())
        .build();

    messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
        ._securityToken_(dat)
        ._correlationMessage_(message.getId())
        ._issued_(IdsMessageUtils.getGregorianNow())
        ._issuerConnector_(connectorConfig.getConnector().getId())
        ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
        ._senderAgent_(connectorConfig.getConnector().getId())
        .build();


    MessageResponse messageResponse=handler.handleMessage((ResourceUnavailableMessageImpl) message,messagePayload);

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

      assertThat(map.get("payload")).isEqualTo("Resource not found with ID : 'https://localhost:8080/localconnector/api/offers/ae8dc3f6-f2ca-4b06-bd76-07c777e130e5'. Did you send a ResourceUpdateMessage yet?");

    }

    catch (IOException e){
      throw new RuntimeException(e);
    }

  }
  
  private Model getConnectorModel(){
    String connector="https://w3id.org/inesctec/stvgodigital/autogen/connectorEndpoint/e5e2ab04-633a-44b9-87d9-a097ae6da3cf, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ConnectorEndpoint\n" +
            "https://w3id.org/inesctec/stvgodigital/autogen/connectorEndpoint/e5e2ab04-633a-44b9-87d9-a097ae6da3cf, https://w3id.org/idsa/core/accessURL, https://vcese12.inesctec.pt:8080/subcontracted/api/ids/data\n" +
            "https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/43f6d7f0-3925-4a8f-b7ce-a7f4e725046c, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ResourceCatalog\n" +
            "https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/43f6d7f0-3925-4a8f-b7ce-a7f4e725046c, http://www.w3.org/2002/07/owl#sameAs, https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/43f6d7f0-3925-4a8f-b7ce-a7f4e725046c\n" +
            "https://w3id.org/inesctec/stvgodigital/autogen/publicKey/20bcebe2-694e-49c5-b2d9-8070e5b09c69, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/PublicKey\n" +
            "https://w3id.org/inesctec/stvgodigital/autogen/publicKey/20bcebe2-694e-49c5-b2d9-8070e5b09c69, https://w3id.org/idsa/core/keyType, https://w3id.org/idsa/code/RSA\n" +
            "https://w3id.org/inesctec/stvgodigital/autogen/publicKey/20bcebe2-694e-49c5-b2d9-8070e5b09c69, https://w3id.org/idsa/core/keyValue, \"VFVsSlFrbHFRVTVDWjJ0eGFHdHBSemwzTUVKQlVVVkdRVUZQUTBGUk9FRk5TVWxDUTJkTFEwRlJSVUY2YUZGS2FucHBZM2t4T0VWTFkxVm9XamhvVDBOQmNYcHdlREpzWjJ4NE5pdGxWbE0zTUc5Q1JVcGlSekp3YkZSS2JVTTRXVUZYV0RCdlZEUnFVMDFoZUcweU0yTmtSMDlOVDNsWGRIQnJUVGQwU0RCVFR6QkRUalZuY2t4c1ZXUkVaMnBuVUVGVWNFWlljM3AyVWpGMU1YaEhiek5rU0hSQmNtMDNNa1EzWm5KTVlXTkdMMXAzWjJjNE1GSjFSVEowS3pkNmRVeG1MM1JSVUdSeVUyTmtXa2gyTmxScE1HeEJiVVJNVGt4a1JtRmhRbVkzUldkMmFtY3pNME5DU2poMFVWZ3JRVVUyZEZweE0xbHhWVFJ1Y0VOelVWTTRNMVpxUjIwMFUweDNTVGxHZERacU5rbzBUa1ZDWVdjd1pXWnlUbUU0WlV0WWFXMDBTMmRWUTFGWU1Xb3hMMjFoYjIxSFZVWTJTMHBFTjJoWlJVZEVNV3QxWW01VlpYQXhWRmhUWW1kWmJITm9ZVVpZVDFWM016QkJOV3RoVUhoNFVHbG1WbWRUTkRNMVV6QlBhRFZzVTJKaFV6bGtRbmc0ZVRoUFpXRTRNbmRKUkVGUlFVST0=\"\n" +
            "https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/5bd90ce2-0f51-4fb7-9b92-cfe441176f1e, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/ResourceCatalog\n" +
            "https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/5bd90ce2-0f51-4fb7-9b92-cfe441176f1e, http://www.w3.org/2002/07/owl#sameAs, https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/5bd90ce2-0f51-4fb7-9b92-cfe441176f1e\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, https://w3id.org/idsa/core/BaseConnector\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, http://www.w3.org/2002/07/owl#sameAs, https://stvgodigital.pt/ids/connector/id/subcontracted-01\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/curator, https://www.inesctec.pt/\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/description \"IDS Connector reference implementation\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/hasDefaultEndpoint, https://w3id.org/inesctec/stvgodigital/autogen/connectorEndpoint/e5e2ab04-633a-44b9-87d9-a097ae6da3cf\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.1.0\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.0\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.5\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.3\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.1\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.6\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.0.0\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.7\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.4\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.1.2\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/inboundModelVersion, \"4.2.2\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/maintainer, https://www.inesctec.pt/\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/outboundModelVersion, \"4.2.7\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/publicKey, https://w3id.org/inesctec/stvgodigital/autogen/publicKey/20bcebe2-694e-49c5-b2d9-8070e5b09c69\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/resourceCatalog, https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/43f6d7f0-3925-4a8f-b7ce-a7f4e725046c\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/resourceCatalog, https://vcese12.inesctec.pt:8080/subcontracted/api/catalogs/5bd90ce2-0f51-4fb7-9b92-cfe441176f1e\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/securityProfile, https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/title, \"Dataspace Connector - STVgoDIGITAL - subcontracted\"\n" +
            "https://stvgodigital.pt/ids/connector/id/subcontracted-01, https://w3id.org/idsa/core/version, \"8.0.9\"";

    // Creates a list using the \n as the separator for the rows
    ArrayList<String> expectedModelToRemoveAsList = new ArrayList<>(Arrays.asList(connector.split("\n")));
    
    FusekiConverter converter=new FusekiConverter();
    Model connectorModel =converter.convertStringsToRDFModel(expectedModelToRemoveAsList);
    connectorModel.listStatements().toList().forEach(System.out::println);

    return connectorModel;
  }
}