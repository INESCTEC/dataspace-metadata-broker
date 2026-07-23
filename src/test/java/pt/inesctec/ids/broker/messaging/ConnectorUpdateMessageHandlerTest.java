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
import org.apache.jena.query.Query;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.dto.ModelDto;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.JenaService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.PAYLOAD;
import static pt.inesctec.ids.broker.config.IDSConstants.core;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties")
class ConnectorUpdateMessageHandlerTest {
  private BrokerService brokerService;
  private ModelService modelService;
  @Autowired
  private FusekiConnectionAccessor accessor;

  MessageProcessedNotificationMessage messageProcessedNotification;

  Map<String, Object> map;
  Serializer serializer;

  TestUtil testUtil=new TestUtil();

  @Autowired
  private ConnectorUpdateMessageHandler handler;

  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  private ConnectorUpdateMessage message;
  private Broker broker;
  private Connector connectorToAdd;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    brokerService=new BrokerService(accessor);
    modelService=new ModelService(accessor);

    broker=testUtil.broker();
    connectorToAdd=testUtil.createConnector();

    DynamicAttributeToken dat=new DynamicAttributeTokenBuilder()._tokenValue_("Fake token")._tokenFormat_(TokenFormat.JWT).build();

    Mockito.when(connectorConfig.getConnector()).thenReturn(broker);
    Mockito.when(connectorConfig.getConfigurationModel()).thenReturn(configurationModel);
    Mockito.when(connectorConfig.getConfigurationModel().getConnectorDeployMode()).thenReturn(ConnectorDeployMode.TEST_DEPLOYMENT);
    Mockito.when(provider.provideDapsToken()).thenReturn("Mock Token");
    Mockito.when(dapsValidator.checkDat(dat)).thenReturn(true);
    Mockito.when(provider.getDAT()).thenReturn(dat);

    /*System.out.println(connectorConfig.getConnector() + " "+ connectorConfig.getConfigurationModel().getConnectorDeployMode());
    System.out.println("Provider: "+provider.getDAT() + " - " +provider.provideDapsToken());*/
    handler=new ConnectorUpdateMessageHandler(connectorConfig, provider, brokerService);

    serializer=new Serializer();


    testUtil.removeAllGraphsFromDB(accessor);

    // Create the Header to send on the handleMessage()
    message=new ConnectorUpdateMessageBuilder()
            ._securityToken_(provider.getDAT())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(connectorConfig.getConnector().getId())
            ._modelVersion_(this.connectorConfig.getConnector().getOutboundModelVersion())
            ._senderAgent_(connectorConfig.getConnector().getId())
            ._affectedConnector_(URI.create("https://test-broker.com/id/test/connector"))
            //._recipientAgent_(message.getRecipientAgent())
            .build();

    messageProcessedNotification= new MessageProcessedNotificationMessageBuilder(URI.create("https://test-broker.com/messageProcessedNotification/id/122454578777"))
            ._securityToken_(provider.getDAT())
            ._correlationMessage_(message.getId())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(connectorConfig.getConnector().getId())
            ._modelVersion_(connectorConfig.getConnector().getOutboundModelVersion())
            ._senderAgent_(connectorConfig.getConnector().getId())
            .build();
  }

  @Test
  @Order(1)
  void handleMessage() {
    try {
      // Create the Payload to send on the handleMessage()
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToAdd.toString()), new ObjectMapper());

      // Send the Message
      MessageResponse messageResponse=handler.handleMessage((ConnectorUpdateMessageImpl) message,messagePayload);

      // Get the Response
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
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }

      try {
        Connector payload = serializer.deserialize(map.get("payload").toString(), Connector.class);
        assertInstanceOf(Connector.class, payload);


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
    }
  }

  /**
   * Tests adding a Connector two times without modifications
   * */
  @Test
  @Order(2)
  void addConnector2Times_ShouldReturnEmpty() {
    try {
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToAdd.toString()), new ObjectMapper());
      MessagePayload messagePayload2=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToAdd.toString()), new ObjectMapper());

      MessageResponse messageResponse=handler.handleMessage((ConnectorUpdateMessageImpl) message,messagePayload);
      MessageResponse messageResponseToGiveError=handler.handleMessage((ConnectorUpdateMessageImpl) message,messagePayload2);

      map=messageResponse.createMultipartMap(serializer);
      Map<String, Object> map2thTime=messageResponseToGiveError.createMultipartMap(serializer);
      try {
        // Assert That the Header is correct and check all values
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

        // Assert That the Header is correct and check all values
        final var requestHeader2thTime = serializer.deserialize((String) map2thTime.get("header"), MessageProcessedNotificationMessage.class);
        assertInstanceOf(MessageProcessedNotificationMessage.class, requestHeader2thTime);
        Assertions.assertTrue(MessageProcessedNotificationMessage.class.isAssignableFrom(requestHeader2thTime.getClass()),
            "Method should return MessageProcessedNotificationMessage. It returns "+ requestHeader2thTime.getClass());
        assertThat(requestHeader2thTime.getSecurityToken()).isNotNull();
        assertThat(requestHeader2thTime.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader2thTime.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader2thTime.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader2thTime.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader2thTime.getIssued()).isNotNull();

        System.out.println(map2thTime.get(PAYLOAD).toString());
      }
      catch (IOException e){
        // Assertation made to fail
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   *  The Connector is registered and disabled;
   * */
  @Test
  @Order(3)
  void addConnectorToEnableIt() {
    // Prepare environment
    brokerService.addConnectorToFuseki(connectorToAdd);
    brokerService.makeConnectorUnavailable(connectorToAdd.getId());
    try {

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToAdd.toString()), new ObjectMapper());

      MessageResponse messageResponse=handler.handleMessage((ConnectorUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      try {
        // Assert That the Header is correct and check all values
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
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }

      //IdsMessageUtils.buildSelfDeclaration()

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   *  The Connector is registered. Then disabled and updated.
   *  Test adds after updating the connector.
   * */
  @Test
  @Order(4)
  void addConnectorAfterUpdates() {
    // Prepare environment
    String sparqlQuery = "PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
        "ASK WHERE  {  ?x ids:physicalLocation ?s } ";
    String graphName=brokerService.addConnectorToFuseki(connectorToAdd);
    // Garantees that this property does not exist
    assertThat(brokerService.executeSPARQLQuery(sparqlQuery)).isEqualTo("false");
    brokerService.makeConnectorUnavailable(connectorToAdd.getId());
    // Update Connector
    connectorToAdd.setPhysicalLocation(new GeoFeatureBuilder().build());
    try {

      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(connectorToAdd.toString()), new ObjectMapper());

      MessageResponse messageResponse=handler.handleMessage((ConnectorUpdateMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      try {
        // Assert That the Header is correct and check all values
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
        Assertions.assertEquals(MessageProcessedNotificationMessage.class, "Error while deserializing", "Method should return a MessageProcessedNotificationMessage.");
      }

      // See if Connector was updated in the Database
      assertThat(brokerService.executeSPARQLQuery(sparqlQuery)).isEqualTo("true");

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

}