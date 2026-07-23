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
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import ids.messaging.common.SerializeException;
import ids.messaging.core.config.ConfigContainer;
import ids.messaging.core.daps.*;
import ids.messaging.handler.message.MessagePayload;
import ids.messaging.handler.message.MessagePayloadInputstream;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.dto.BrokerSelfDescription;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.util.CompareUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.DEPTH;
import static pt.inesctec.ids.broker.config.IDSConstants.HEADER;
import static pt.inesctec.ids.broker.config.ResponseType.NTRIPLES;
import static pt.inesctec.ids.broker.config.ResponseType.TURTLE;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties")
class DescriptionRequestMessageHandlerTest {
  Serializer serializer;

  TestUtil testUtil=new TestUtil();

  Map<String, Object> map;

  @Autowired private BrokerSelfDescription brokerSelfDescription;
  @Autowired private FusekiConnectionAccessor accessor;
  @Autowired private BrokerService brokerService;
  @Autowired
  private DescriptionRequestMessageHandler handler;

  @Autowired @Qualifier("catalogURI")
  private String catalogURI;

  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  private DescriptionRequestMessage message;

  private MessagePayload messagePayload;
  Broker broker;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    testUtil.removeAllGraphsFromDB(accessor);

    broker=testUtil.broker();

    DynamicAttributeToken dat=new DynamicAttributeTokenBuilder()._tokenValue_("Fake token")._tokenFormat_(TokenFormat.JWT).build();

    Mockito.when(connectorConfig.getConnector()).thenReturn(broker);
    Mockito.when(connectorConfig.getConfigurationModel()).thenReturn(configurationModel);
    Mockito.when(connectorConfig.getConfigurationModel().getConnectorDeployMode()).thenReturn(ConnectorDeployMode.TEST_DEPLOYMENT);
    Mockito.when(provider.provideDapsToken()).thenReturn("Mock Token");
    Mockito.when(dapsValidator.checkDat(dat)).thenReturn(true);
    Mockito.when(provider.getDAT()).thenReturn(dat);

    handler=new DescriptionRequestMessageHandler(connectorConfig, provider, accessor, brokerSelfDescription, catalogURI);
    serializer=new Serializer();

    // Create the Header to send on the handleMessage()
    message=new DescriptionRequestMessageBuilder()
            ._securityToken_(provider.getDAT())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(connectorConfig.getConnector().getId())
            ._modelVersion_(this.connectorConfig.getConnector().getOutboundModelVersion())
            ._senderAgent_(connectorConfig.getConnector().getId())
            ._recipientConnector_(URI.create("https://localwslhost:8082/api/ids/data"))
            ._recipientAgent_(URI.create("https://localwslhost:8082/api/ids/data"))
            .build();

    messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream("test string"), new ObjectMapper());

  }

  @Test
  void handleMessage() {
    try {

      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("header").getClass());
      System.out.println(map);
     /* System.out.println(map.get("header"));
      System.out.println(map.get("payload"));*/
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);
        Assertions.assertTrue(DescriptionResponseMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();

        assertThat((((String) map.get("header")).contains("elementType"))).isTrue();
        assertThat((((String) map.get("header")).contains("Serialization"))).isTrue();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
      //IdsMessageUtils.buildSelfDeclaration()
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessageWithPath() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    try {
      message=new DescriptionRequestMessageBuilder()
              ._securityToken_(provider.getDAT())
              ._issued_(IdsMessageUtils.getGregorianNow())
              ._issuerConnector_(connectorConfig.getConnector().getId())
              ._modelVersion_(this.connectorConfig.getConnector().getOutboundModelVersion())
              ._senderAgent_(connectorConfig.getConnector().getId())
              ._recipientConnector_(URI.create("https://localwslhost:8082/test/api/ids/data"))
              ._recipientAgent_(URI.create("https://localwslhost:8082/test/api/ids/data"))
              //._requestedElement_()
              .build();

      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("header").getClass());
      System.out.println(map);
     /* System.out.println(map.get("header"));
      System.out.println(map.get("payload"));*/
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);
        Assertions.assertTrue(DescriptionResponseMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();

        assertThat((((String) map.get("header")).contains("elementType"))).isTrue();
        assertThat((((String) map.get("header")).contains("Serialization"))).isTrue();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
      //IdsMessageUtils.buildSelfDeclaration()
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors/";
   * depth=0 (default)
   * */
  @Test
  void handleMessage_WithRequestElement() {
    // Prepare environment
    Connector connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(testUtil.createConnectorWithoutResourceCatalog());
    brokerService.addResourceToFuseki(testUtil.createResource(), connectorProducer.getId());
    try {
      message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/"));
      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);
        Assertions.assertTrue(DescriptionResponseMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        // Get the additional header values  because the Fraunhoffer Serializer loses the properties
        DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
        String elementType = ctx.read("$.elementType['@id']", String.class);
        String serialization = ctx.read("Serialization", String.class);
        assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");
        assertThat(serialization).isEqualTo("JSON-LD-10/compact pretty");

      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      //System.out.println(map.get("payload").getClass());

      try {
        final var requestPayload = serializer.deserialize(map.get("payload").toString(), ConnectorCatalog.class);
        Assertions.assertTrue(ConnectorCatalogImpl.class.isAssignableFrom(requestPayload.getClass()),
                "Method should return ConnectorCatalog. It returns "+ requestPayload.getClass());

        //System.err.println("Size: "+new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size());
        assertThat(new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size())
            .isEqualTo(84);
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors/";
   * depth=10
   * */
  @Test
  void handleMessage_WithRequestElement_Depth10() {
    // Prepare environment
    Connector connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(testUtil.createConnectorWithoutResourceCatalog());
    brokerService.addResourceToFuseki(testUtil.createResource(), connectorProducer.getId());
    try {
      message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/"));
      message.setProperty(DEPTH, 10);

      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);
        Assertions.assertTrue(DescriptionResponseMessage.class.isAssignableFrom(requestHeader.getClass()),
            "Method should return ResultMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        // Get the additional header values  because the Fraunhoffer Serializer loses the properties
        DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
        String elementType = ctx.read("$.elementType['@id']", String.class);
        String serialization = ctx.read("Serialization", String.class);
        assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");
        assertThat(serialization).isEqualTo("JSON-LD-10/compact pretty");

      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      //System.out.println(map.get("payload").getClass());

      try {
        final var requestPayload = serializer.deserialize(map.get("payload").toString(), ConnectorCatalog.class);
        Assertions.assertTrue(ConnectorCatalogImpl.class.isAssignableFrom(requestPayload.getClass()),
            "Method should return ConnectorCatalog. It returns "+ requestPayload.getClass());

        //System.err.println("Size: "+new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size());
        assertThat(new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size())
            .isEqualTo(282);
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors/";
   * depth=1 (Only the Connectors should appear)
   * */
  @Test
  void handleMessage_WithRequestElement_Depth1() {
    // Prepare environment
    Connector connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(testUtil.createConnectorWithoutResourceCatalog());
    brokerService.addResourceToFuseki(testUtil.createResource(), connectorProducer.getId());
    try {
      message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/"));
      message.setProperty(DEPTH, 1);
      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      //System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);
        Assertions.assertTrue(DescriptionResponseMessage.class.isAssignableFrom(requestHeader.getClass()),
            "Method should return ResultMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();

        // Get the additional header values  because the Fraunhoffer Serializer loses the properties
        DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
        String elementType = ctx.read("$.elementType['@id']", String.class);
        String serialization = ctx.read("Serialization", String.class);
        assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");
        assertThat(serialization).isEqualTo("JSON-LD-10/compact pretty");
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload").getClass());

      try {
        final var requestPayload = serializer.deserialize(map.get("payload").toString(), ConnectorCatalog.class);
        Assertions.assertTrue(ConnectorCatalogImpl.class.isAssignableFrom(requestPayload.getClass()),
            "Method should return ConnectorCatalog. It returns "+ requestPayload.getClass());

        System.err.println("Size: "+new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size());
        assertThat(new ArrayList<>(Arrays.asList(requestPayload.toString().split("\n"))).size())
            .isEqualTo(170);
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors";
   * Should return an empty ConnectorList, since there are no active connectors
   * */
  @Test
  void handleMessage_WithRequestElement_NoActiveConnectors () throws SerializeException {
    // NO ACTIVE CONNETORS -> Response should be:
//    {
//      "@id" : "http://test-hostname:8082/connectors/",
//        "@type" : "ids:ConnectorCatalog",
//        "@context" : {
//      "owl" : "http://www.w3.org/2002/07/owl#",
//          "ids" : "https://w3id.org/idsa/core/"
//    }
//    }

    message.setRequestedElement(URI.create("http://test-hostname:8082/connectors"));
    MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

    map=messageResponse.createMultipartMap(serializer);
    System.out.println(map.get("payload"));
    try {
      final var requestHeader = serializer.deserialize((String) map.get("header"), Message.class);
      assertInstanceOf(DescriptionResponseMessage.class, requestHeader);

      assertThat(requestHeader.getSecurityToken()).isNotNull();
      assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
      assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
      assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
      assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
      assertThat(requestHeader.getIssued()).isNotNull();

      // Get the additional header values  because the Fraunhoffer Serializer loses the properties
      DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
      String elementType = ctx.read("$.elementType['@id']", String.class);
      String serialization = ctx.read("Serialization", String.class);
      assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");
      assertThat(serialization).isEqualTo("JSON-LD-10/compact pretty");
    }
    catch (IOException e){
      throw new RuntimeException(e.toString());
    }

    try {
      final var requestPayload = serializer.deserialize(map.get("payload").toString(), ConnectorCatalog.class);
      assertTrue(ConnectorCatalog.class.isAssignableFrom(requestPayload.getClass()));

      // I Cannot use the 'requestPayload' because it was 'converted' into ConnectorCatalog. Therefore, the output is now different
      assertThat(map.get("payload").toString().replaceAll("\\R", "\n")).isEqualTo("{\n" +
          "  \"@id\" : \"http://test-hostname:8082/connectors/\",\n" +
          "  \"@type\" : \"ids:ConnectorCatalog\",\n" +
          "  \"@context\" : {\n" +
          "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
          "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
          "  }\n" +
          "}\n".toString().replaceAll("\\R", "\n"));

    }
    catch (IOException e){
      throw new RuntimeException(e.toString());
    }
  }


  /**
   * RequestedElement = "http://localwslhost:8082/connectors". It is an invalid path
   * Should return an error
   * */
  @Test
  void handleMessage_WithRequestElement_InvalidPath() {
      message.setRequestedElement(URI.create("http://localwslhost:8083/connectores/"));
      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      try {
        map=messageResponse.createMultipartMap(serializer);
      } catch (SerializeException e) {
        throw new RuntimeException(e);
      }

    //System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);
        assertInstanceOf(RejectionMessage.class, requestHeader);
        Assertions.assertTrue(RejectionMessage.class.isAssignableFrom(requestHeader.getClass()),
            "Method should return RejectionMessage. It returns " + requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      final var requestPayload = map.get("payload");
      assertThat(requestPayload).isInstanceOf(String.class);
      assertThat(requestPayload).isEqualTo("The requested resource was not found");

  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors/connector-producer/-846787637";
   * Depth=1
   * */
  @Test
  void handleMessage_WithRequestElement_SpecificConnector_Depth1() throws SerializeException{
    // Prepare environment
    Connector connector=testUtil.createConnector();
    brokerService.addConnectorToFuseki(connector);
    brokerService.addConnectorToFuseki(testUtil.createConnectorSubcontracted());
    brokerService.addResourceToFuseki(testUtil.createResource(), connector.getId());

    message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/connector-producer/-846787637"));
    message.setProperty(DEPTH, 1);

    MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

    map=messageResponse.createMultipartMap(serializer);
    System.err.println(map.get("payload").getClass());

    // Check the response

    // Header
    try {
      final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
      assertInstanceOf(DescriptionResponseMessage.class, requestHeader);
      Assertions.assertTrue(DescriptionResponseMessage.class.isAssignableFrom(requestHeader.getClass()),
          "Method should return ResultMessage. It returns "+ requestHeader.getClass());

      assertThat(requestHeader.getSecurityToken()).isNotNull();
      assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
      assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
      assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
      assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
      assertThat(requestHeader.getIssued()).isNotNull();

      // Get the additional header values  because the Fraunhoffer Serializer loses the properties
      DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
      String elementType = ctx.read("$.elementType['@id']", String.class);
      String serialization = ctx.read("Serialization", String.class);
      assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/BaseConnector");
      assertThat(serialization).isEqualTo("JSON-LD-10/compact pretty");
    }
    catch (IOException e){
      throw new RuntimeException(e.toString());
    }

    // Payload
    String requestPayload = map.get("payload").toString();
    assertThat(requestPayload).isInstanceOf(String.class);


    String expectedResponse=responseFrom_handleMessage_WithRequestElement_SpecificConnector_Depth1();
    assertThat(requestPayload.replaceAll("\\R", "\n")).isEqualTo(expectedResponse.replaceAll("\\R", "\n"));
    assertThat(CompareUtil.compareJsonStrings(requestPayload, expectedResponse)).isTrue();

  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors/connector-producer/-846787637";
   * Should return an error, since there are no active connectors
   * */
  @Test
  void handleMessage_WithRequestElement_SpecificConnector_SpecificResource_NoActiveConnectors() {
    // This test should return
    /*{
      "details": {
      "reason": {
        "properties": null,
                "@id": "https://w3id.org/idsa/code/NOT_FOUND"
      },
      "payload": "The requested resource was not found",
              "type": "de.fraunhofer.iais.eis.RejectionMessageImpl"
    },
      "message": "Received unexpected response message."
    }*/
    try {

      message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/connector-producer/-846787637"));
      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.toString());
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);
        assertInstanceOf(RejectionMessage.class, requestHeader);
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.NOT_FOUND);
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      Assertions.assertEquals(map.get("payload"), "The requested resource was not found");

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * RequestedElement = "http://test-hostname:8082/connectors/";
   * depth=1
   * accept=TURTLE
   * */
  @Test
  void handleMessage_WithRequestElement_responseInTurtle() {
    // Prepare environment
    Connector connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    brokerService.addConnectorToFuseki(testUtil.createConnectorWithoutResourceCatalog());
    brokerService.addResourceToFuseki(testUtil.createResource(), connectorProducer.getId());
    try {
      message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/"));
      message.setProperty(DEPTH, 1);
      message.setProperty("https://w3id.org/idsa/core/accept", new TypedLiteral(TURTLE + "," + NTRIPLES));
      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      //System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();

        // Get the additional header values  because the Fraunhoffer Serializer loses the properties
        DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
        String elementType = ctx.read("$.elementType['@id']", String.class);
        String serialization = ctx.read("Serialization", String.class);
        assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");
        assertThat(serialization).isEqualTo("Turtle/pretty");
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload").getClass());

      System.err.println("Size: "+new ArrayList<>(Arrays.asList(map.get("payload").toString().split("\n"))).size());
      assertThat(new ArrayList<>(Arrays.asList(map.get("payload").toString().split("\n"))).size())
          .isEqualTo(93);

      assertThat(map.get("payload").toString()).isEqualTo("@prefix ids: <https://w3id.org/idsa/core/> .\n" +
          "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466>\n" +
          "        a             ids:PublicKey ;\n" +
          "        owl:sameAs    <https://test-broker.com/id/test/publicKey/gtedcsd> ;\n" +
          "        ids:keyType   <https://w3id.org/idsa/code/RSA> ;\n" +
          "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\n" +
          "        a             ids:PublicKey ;\n" +
          "        owl:sameAs    <https://test-broker.com/id/test/publicKey/dhiasdcna> ;\n" +
          "        ids:keyType   <https://w3id.org/idsa/code/RSA> ;\n" +
          "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\n" +
          "        a              ids:ConnectorEndpoint ;\n" +
          "        owl:sameAs     <https://test-broker.com/id/test/connectorEndpoint/dhiasdcna> ;\n" +
          "        ids:accessURL  <https://host.docker.internal:8080/test/connector/api/ids/data> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134>\n" +
          "        a           ids:ResourceCatalog ;\n" +
          "        owl:sameAs  <https://test-broker.com/id/test/multipleCatalogs/catalog/2/api/connector-producer> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/>\n" +
          "        a                    ids:ConnectorCatalog ;\n" +
          "        ids:listedConnector  <http://test-hostname:8082/connectors/connector-producer/-846787637> , <http://test-hostname:8082/connectors/connector-producer/-1374631466> , <http://test-hostname:8082/connectors/connector-subcontracted/-821347780> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950>\n" +
          "        a              ids:ConnectorEndpoint ;\n" +
          "        owl:sameAs     <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> ;\n" +
          "        ids:accessURL  <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195>\n" +
          "        a                    ids:ResourceCatalog ;\n" +
          "        owl:sameAs           <https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer> ;\n" +
          "        ids:offeredResource  <http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195/-435574692> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561>\n" +
          "        a           ids:ResourceCatalog ;\n" +
          "        owl:sameAs  <https://test-broker.com/id/test/catalog/api/connector-subcontracted> .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780>\n" +
          "        a                         ids:BaseConnector ;\n" +
          "        owl:sameAs                <https://test-broker.com/id/test/connector-subcontracted> ;\n" +
          "        ids:curator               <https://www.inesctec.pt/> ;\n" +
          "        ids:description           \"Description\" ;\n" +
          "        ids:hasDefaultEndpoint    <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> ;\n" +
          "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
          "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
          "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
          "        ids:publicKey             <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> ;\n" +
          "        ids:resourceCatalog       <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> ;\n" +
          "        ids:securityProfile       <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> ;\n" +
          "        ids:title                 \"Title\" .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034>\n" +
          "        a             ids:PublicKey ;\n" +
          "        owl:sameAs    <https://test-broker.com/id/test/multipleCatalogs/publicKey/dferstgse> ;\n" +
          "        ids:keyType   <https://w3id.org/idsa/code/RSA> ;\n" +
          "        ids:keyValue  \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466>\n" +
          "        a                         ids:BaseConnector ;\n" +
          "        owl:sameAs                <https://test-broker.com/id/test/multipleCatalogs/connector-producer> ;\n" +
          "        ids:curator               <https://www.inesctec.pt/> ;\n" +
          "        ids:description           \"Description\" ;\n" +
          "        ids:hasDefaultEndpoint    <http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774> ;\n" +
          "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
          "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
          "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
          "        ids:publicKey             <http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034> ;\n" +
          "        ids:resourceCatalog       <http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195> , <http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134> ;\n" +
          "        ids:securityProfile       <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> ;\n" +
          "        ids:title                 \"Title\" .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-846787637>\n" +
          "        a                         ids:BaseConnector ;\n" +
          "        owl:sameAs                <https://test-broker.com/id/test/connector-producer> ;\n" +
          "        ids:curator               <https://www.inesctec.pt/> ;\n" +
          "        ids:description           \"Description\" ;\n" +
          "        ids:hasDefaultEndpoint    <http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807> ;\n" +
          "        ids:inboundModelVersion   \"4.2.6\" ;\n" +
          "        ids:maintainer            <https://www.inesctec.pt/> ;\n" +
          "        ids:outboundModelVersion  \"4.2.6\" ;\n" +
          "        ids:publicKey             <http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891> ;\n" +
          "        ids:securityProfile       <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> ;\n" +
          "        ids:title                 \"Title\" .\n" +
          "\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774>\n" +
          "        a              ids:ConnectorEndpoint ;\n" +
          "        owl:sameAs     <https://test-broker.com/id/test/multipleCatalogs/connectorEndpoint/dhiasdcna> ;\n" +
          "        ids:accessURL  <https://host.docker.internal:8080/test/multipleCatalogs/connector/api/ids/data> .\n");
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * RequestedElement = "http://test-hostname:8082/connectors/";
   * depth=1
   * accept=TRIPLES
   * */
  @Test
  void handleMessage_WithRequestElement_responseInTriples() {
    // Prepare environment
    Connector connectorProducer=testUtil.createConnectorWithMultipleCatalogs();
    Connector connectorSubcontracted=testUtil.createConnectorSubcontracted();
    brokerService.addConnectorToFuseki(connectorProducer);
    brokerService.addConnectorToFuseki(connectorSubcontracted);
    try {
      message.setRequestedElement(URI.create("http://test-hostname:8082/connectors/"));
      message.setProperty(DEPTH, 1);
      message.setProperty("https://w3id.org/idsa/core/accept", new TypedLiteral(NTRIPLES));

      MessageResponse messageResponse=handler.handleMessage((DescriptionRequestMessageImpl) message,messagePayload);

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), DescriptionResponseMessage.class);
        assertInstanceOf(DescriptionResponseMessage.class, requestHeader);

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();

        // Get the additional header values  because the Fraunhoffer Serializer loses the properties
        DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
        String elementType = ctx.read("$.elementType['@id']", String.class);
        String serialization = ctx.read("Serialization", String.class);
        assertThat(elementType).isEqualTo("https://w3id.org/idsa/core/ConnectorCatalog");
        assertThat(serialization).isEqualTo("N-Triples/utf-8");
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload").getClass());

      System.err.println("Size: "+new ArrayList<>(Arrays.asList(map.get("payload").toString().split("\n"))).size());
      assertThat(new ArrayList<>(Arrays.asList(map.get("payload").toString().split("\n"))).size())
          .isEqualTo(48);

      assertThat(CompareUtil.compareStrings(map.get("payload").toString(), "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <https://w3id.org/idsa/core/keyValue> \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <https://w3id.org/idsa/core/keyType> <https://w3id.org/idsa/code/RSA> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/publicKey/gtedcsd> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/PublicKey> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/multipleCatalogs/catalog/2/api/connector-producer> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ResourceCatalog> .\n" +
          "<http://test-hostname:8082/connectors/> <https://w3id.org/idsa/core/listedConnector> <http://test-hostname:8082/connectors/connector-producer/-1374631466> .\n" +
          "<http://test-hostname:8082/connectors/> <https://w3id.org/idsa/core/listedConnector> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780> .\n" +
          "<http://test-hostname:8082/connectors/> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ConnectorCatalog> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> <https://w3id.org/idsa/core/accessURL> <https://host.docker.internal:8080/test/subcontracted/api/ids/data> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/connectorEndpoint/gtedcsd> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ConnectorEndpoint> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/multipleCatalogs/catalog/api/connector-producer> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ResourceCatalog> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/catalog/api/connector-subcontracted> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ResourceCatalog> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/outboundModelVersion> \"4.2.6\" .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/securityProfile> <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/BaseConnector> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/resourceCatalog> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/curator> <https://www.inesctec.pt/> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/hasDefaultEndpoint> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-489179950> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/description> \"Description\" .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/title> \"Title\" .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/publicKey> <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-833258466> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/connector-subcontracted> .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.6\" .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780> <https://w3id.org/idsa/core/maintainer> <https://www.inesctec.pt/> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034> <https://w3id.org/idsa/core/keyValue> \"S2V5VmFsdWVFeGFtcGxl\" .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034> <https://w3id.org/idsa/core/keyType> <https://w3id.org/idsa/code/RSA> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/multipleCatalogs/publicKey/dferstgse> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/PublicKey> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/resourceCatalog> <http://test-hostname:8082/connectors/connector-producer/-1374631466/1147089195> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/maintainer> <https://www.inesctec.pt/> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/outboundModelVersion> \"4.2.6\" .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/inboundModelVersion> \"4.2.6\" .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/BaseConnector> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/description> \"Description\" .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/publicKey> <http://test-hostname:8082/connectors/connector-producer/-1374631466/471366034> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/resourceCatalog> <http://test-hostname:8082/connectors/connector-producer/-1374631466/116765134> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/multipleCatalogs/connector-producer> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/securityProfile> <https://w3id.org/idsa/code/BASE_SECURITY_PROFILE> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/title> \"Title\" .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/curator> <https://www.inesctec.pt/> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466> <https://w3id.org/idsa/core/hasDefaultEndpoint> <http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774> <https://w3id.org/idsa/core/accessURL> <https://host.docker.internal:8080/test/multipleCatalogs/connector/api/ids/data> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774> <http://www.w3.org/2002/07/owl#sameAs> <https://test-broker.com/id/test/multipleCatalogs/connectorEndpoint/dhiasdcna> .\n" +
          "<http://test-hostname:8082/connectors/connector-producer/-1374631466/1690340774> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://w3id.org/idsa/core/ConnectorEndpoint> ."))
          .isTrue();
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  private String responseFrom_handleMessage_WithRequestElement_SpecificConnector_Depth1(){
    return "{\n" +
        "  \"@graph\" : [ {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637\",\n" +
        "    \"@type\" : \"ids:BaseConnector\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/connector-producer\",\n" +
        "    \"curator\" : \"https://www.inesctec.pt/\",\n" +
        "    \"description\" : \"Description\",\n" +
        "    \"hasDefaultEndpoint\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807\",\n" +
        "    \"inboundModelVersion\" : \"4.2.6\",\n" +
        "    \"maintainer\" : \"https://www.inesctec.pt/\",\n" +
        "    \"outboundModelVersion\" : \"4.2.6\",\n" +
        "    \"publicKey\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891\",\n" +
        "    \"resourceCatalog\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664\",\n" +
        "    \"securityProfile\" : \"https://w3id.org/idsa/code/BASE_SECURITY_PROFILE\",\n" +
        "    \"title\" : \"Title\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891\",\n" +
        "    \"@type\" : \"ids:PublicKey\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/publicKey/dhiasdcna\",\n" +
        "    \"keyType\" : \"https://w3id.org/idsa/code/RSA\",\n" +
        "    \"keyValue\" : \"S2V5VmFsdWVFeGFtcGxl\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807\",\n" +
        "    \"@type\" : \"ids:ConnectorEndpoint\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/connectorEndpoint/dhiasdcna\",\n" +
        "    \"accessURL\" : \"https://host.docker.internal:8080/test/connector/api/ids/data\"\n" +
        "  }, {\n" +
        "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664\",\n" +
        "    \"@type\" : \"ids:ResourceCatalog\",\n" +
        "    \"sameAs\" : \"https://test-broker.com/id/test/catalog/api/connector-producer\",\n" +
        "    \"offeredResource\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692\"\n" +
        "  } ],\n" +
        "  \"@context\" : {\n" +
        "    \"hasDefaultEndpoint\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/hasDefaultEndpoint\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"curator\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/curator\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"securityProfile\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/securityProfile\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"maintainer\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/maintainer\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"title\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
        "    },\n" +
        "    \"inboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/inboundModelVersion\"\n" +
        "    },\n" +
        "    \"resourceCatalog\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/resourceCatalog\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"sameAs\" : {\n" +
        "      \"@id\" : \"http://www.w3.org/2002/07/owl#sameAs\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"outboundModelVersion\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/outboundModelVersion\"\n" +
        "    },\n" +
        "    \"publicKey\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/publicKey\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"description\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/description\"\n" +
        "    },\n" +
        "    \"accessURL\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/accessURL\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"offeredResource\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/offeredResource\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"keyValue\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyValue\"\n" +
        "    },\n" +
        "    \"keyType\" : {\n" +
        "      \"@id\" : \"https://w3id.org/idsa/core/keyType\",\n" +
        "      \"@type\" : \"@id\"\n" +
        "    },\n" +
        "    \"owl\" : \"http://www.w3.org/2002/07/owl#\",\n" +
        "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
        "  }\n" +
        "}\n";
  }

  private String getSerialization(/*Map<String, String> map*/){
    // Get the additional header values  because the Fraunhoffer Serializer loses the properties
    DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
    return ctx.read("Serialization", String.class);
  }

  private String getElementType(/*Map<String, String> map*/){
    // Get the additional header values  because the Fraunhoffer Serializer loses the properties
    DocumentContext ctx = JsonPath.parse(map.get(HEADER).toString());
    return ctx.read("$.elementType['@id']", String.class);
  }
}