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
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.AdminGraphService;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ConnectorUnavailableMessageHandlerTest {
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
  private ConnectorUnavailableMessageHandler handler;

  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  ConnectorUnavailableMessage message;

  Connector connectorToUnsubscribe;
  Broker broker;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    brokerService=new BrokerService(accessor);
    modelService=new ModelService(accessor);
    adminGraphService=new AdminGraphService(accessor);

    // This needs to a Connector because the data from this fiels will be used to make the Connector unavailable
    connectorToUnsubscribe=testUtil.createConnector();

    broker=testUtil.broker();

    DynamicAttributeToken dat=new DynamicAttributeTokenBuilder()._tokenValue_("Fake token")._tokenFormat_(TokenFormat.JWT).build();

    Mockito.when(connectorConfig.getConnector()).thenReturn(broker);
    Mockito.when(connectorConfig.getConfigurationModel()).thenReturn(configurationModel);
    Mockito.when(connectorConfig.getConfigurationModel().getConnectorDeployMode()).thenReturn(ConnectorDeployMode.TEST_DEPLOYMENT);
    Mockito.when(provider.provideDapsToken()).thenReturn("Mock Token");
    Mockito.when(dapsValidator.checkDat(dat)).thenReturn(true);
    Mockito.when(provider.getDAT()).thenReturn(dat);

    /*System.out.println(connectorConfig.getConnector() + " "+ connectorConfig.getConfigurationModel().getConnectorDeployMode());
    System.out.println("Provider: "+provider.getDAT() + " - " +provider.provideDapsToken());*/
    handler=new ConnectorUnavailableMessageHandler(connectorConfig, provider, brokerService);

    serializer=new Serializer();

    testUtil.removeAllGraphsFromDB(accessor);
    try {

      // Create the Header to send on the handleMessage()
      message=new ConnectorUnavailableMessageBuilder()
              ._securityToken_(provider.getDAT())
              //._correlationMessage_(message.getId())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(connectorToUnsubscribe.getId())
              ._modelVersion_(connectorToUnsubscribe.getOutboundModelVersion())
              ._senderAgent_(connectorToUnsubscribe.getId())
              ._affectedConnector_(connectorToUnsubscribe.getId())
              //._recipientAgent_(message.getRecipientAgent())
              .build();

      // Create the Response Message to Compare with the one returned from the handleMessage()
      messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
              ._securityToken_(provider.getDAT())
              ._correlationMessage_(message.getId())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(connectorConfig.getConnector().getId())
              ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
              ._senderAgent_(connectorConfig.getConnector().getId())
              .build();

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (ConnectorMissingCertExtensionException | DapsConnectionException | DapsEmptyResponseException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(1)
  void handleMessage() {

    testUtil.removeAllGraphsFromDB(accessor);
    try {
      //Connector conn=testUtil.createConnector();

      // Add the Connector to DB
      brokerService.addConnectorToFuseki(connectorToUnsubscribe);

      // Create the Payload to send on the handleMessage()
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToUnsubscribe.toString()), new ObjectMapper());

      // Send the Message
      MessageResponse messageResponse=handler.handleMessage((ConnectorUnavailableMessageImpl) message,messagePayload);

      // Get the Response
      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(connectorToUnsubscribe.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }

      // Get GraphName
      Optional<String> graphName=modelService
              .convertConnectorIDToGraphName(connectorToUnsubscribe.getId());

      // GET Admin Graph
      ModelDto adminGraph= adminGraphService.getGraph();
      //Check if the Connector is inactive
      assertEquals(
              adminGraph.listStatementsWithSubjectEqualTo(URI.create(graphName.get())).stream().findFirst().get().getObject().asLiteral().getBoolean(),
              false);

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException | MessageHandlerException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(2)
  void handleMessage_ConnectorNotRegistered() {

    testUtil.removeAllGraphsFromDB(accessor);
    try {

      // Create the Payload to send on the handleMessage()
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToUnsubscribe.toString()), new ObjectMapper());

      // Send the Message
      MessageResponse messageResponse=handler.handleMessage((ConnectorUnavailableMessageImpl) message,messagePayload);

      // Get the Response
      map=messageResponse.createMultipartMap(serializer);
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

        assertThat(map.get("payload")).isEqualTo("Connector not found with ID 'https://test-broker.com/id/test/connector-producer'. Did you send a ConnectorUpdateMessage yet?");
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }
      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException | MessageHandlerException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Order(3)
  void handleMessage_ConnectorAlreadyInactive() {

    testUtil.removeAllGraphsFromDB(accessor);
    try {
      // Add the Connector to DB
      brokerService.addConnectorToFuseki(connectorToUnsubscribe);

      // Deactivate Connector
      brokerService.makeConnectorUnavailable(connectorToUnsubscribe.getId());

      // Create the Payload to send on the handleMessage()
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToUnsubscribe.toString()), new ObjectMapper());

      // Send the Message
      MessageResponse messageResponse=handler.handleMessage((ConnectorUnavailableMessageImpl) message,messagePayload);

      // Get the Response
      map=messageResponse.createMultipartMap(serializer);
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

        assertThat(map.get("payload")).isEqualTo("The Connector you are trying to remove ('https://test-broker.com/id/test/connector-producer') was not found. Did you send a ConnectorUpdateMessage yet?");
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }
      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException | MessageHandlerException e) {
      throw new RuntimeException(e);
    }
  }

}