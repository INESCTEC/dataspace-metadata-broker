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
import de.fraunhofer.iais.eis.ids.component.core.MessageAndPayload;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import ids.messaging.common.SerializeException;
import ids.messaging.core.config.ConfigContainer;
import ids.messaging.core.daps.*;
import ids.messaging.handler.message.MessagePayload;
import ids.messaging.handler.message.MessagePayloadInputstream;
import ids.messaging.response.ErrorResponse;
import ids.messaging.response.MessageResponse;
import ids.messaging.util.IdsMessageUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.riot.RDFLanguages;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import pt.inesctec.ids.broker.config.ResponseType;
import pt.inesctec.ids.broker.service.BrokerService;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.CompareUtil;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;
import pt.inesctec.ids.broker.util.TestUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static pt.inesctec.ids.broker.config.IDSConstants.PAYLOAD;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
        locations = "classpath:application-test.properties",
        properties = {"logging.level.pt.inesctec.ids.broker.messaging=debug", "logging.level.pt.inesctec.ids.broker.service=info"})
class QueryMessageHandlerTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryMessageHandlerTest.class);

  private BrokerService brokerService;

  Map<String, Object> map;
  Serializer serializer;
  Connector connector_producer, connector_subcontracted, connector_producer2;
  Resource resource_producer, resource_subcontracted;
  TestUtil testUtil;

  @Autowired
  private FusekiConnectionAccessor accessor;

  @Autowired
  private QueryMessageHandler handler;

  @Mock
  private ConfigContainer connectorConfig;

  @Mock
  DapsTokenProvider provider;

  @MockBean
  private ConfigurationModel configurationModel;

  @MockBean
  private DapsValidator dapsValidator;

  private QueryMessage message;
  private Broker broker;
  private Connector connectorToQuery;

  @BeforeEach
  void setUp() throws ConnectorMissingCertExtensionException, DapsConnectionException, DapsEmptyResponseException {
    brokerService=new BrokerService(accessor);
    testUtil=new TestUtil();

    broker=testUtil.broker();
    connectorToQuery=new BaseConnectorBuilder(URI.create("https://test-broker.com/id/test/connector"))
            ._hasDefaultEndpoint_(
                    new ConnectorEndpointBuilder(URI.create("https://test-broker.com/id/test/connectorEndpoint/dhiasdcna"))
                            ._accessURL_(URI.create("https://host.docker.internal:8080/circthread-01/api/ids/data")).build())
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

    handler=new QueryMessageHandler(connectorConfig, provider, brokerService);

    serializer=new Serializer();

    // Create the Header to send on the handleMessage() -> Here the issuerConnector is the Connector not the Broker
    message=new QueryMessageBuilder()
            ._securityToken_(provider.getDAT())
            //._correlationMessage_(message.getId())
            ._issued_(IdsMessageUtils.getGregorianNow())
            ._issuerConnector_(connectorToQuery.getId())
            ._modelVersion_(connectorToQuery.getOutboundModelVersion())
            ._senderAgent_(connectorToQuery.getId())
            ._recipientAgent_(connectorToQuery.getHasDefaultEndpoint().getAccessURL()) // https://host.docker.internal:8080/circthread-01/api/ids/data
            ._queryScope_(QueryScope.ALL)
            ._recipientScope_(QueryTarget.BROKER)
            ._queryLanguage_(QueryLanguage.SPARQL)
            .build();

    connector_producer=testUtil.createConnector();
    connector_subcontracted=testUtil.createConnectorSubcontracted();
    connector_producer2=testUtil.createConnectorWithMultipleCatalogs();
    resource_producer=testUtil.createResource();
    resource_subcontracted=testUtil.createResourceWithMultipleArtefacts();


    LOGGER.info("Clearing database...");
    testUtil.removeAllGraphsFromDB(accessor);
    LOGGER.info("... Database cleared.");

    LOGGER.info("Adding Connectors to database...");
    brokerService.addConnectorToFuseki(connector_producer);
    brokerService.addConnectorToFuseki(connector_subcontracted);
    brokerService.addConnectorToFuseki(connector_producer2);
    LOGGER.info("... Connectors added.");

    LOGGER.info("Adding Resources to database...");
    brokerService.addResourceToFuseki(resource_producer, connector_producer.getId());
    brokerService.addResourceToFuseki(resource_subcontracted, connector_subcontracted.getId());
    LOGGER.info("... Resources added.");

    LOGGER.info("Making one Connector inactive...");
    brokerService.makeConnectorUnavailable(connector_producer2.getId());
    LOGGER.info("... Connector inactive.");

  }

  @Test
  void handleMessage_select() {
    try {
      String query="SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());

        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        // Assertation made to fail
        throw new RuntimeException(e.toString());
      }
      assertEquals(map.get("payload").getClass(), String.class);
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_select_CompareExpectedOutputWithOutput() {
    LOGGER.info("Removing Resource from database...");
    brokerService.removeResourceFromFuseki(connector_producer.getId(), resource_producer.getId());
    LOGGER.info("... Resources removed.");

    LOGGER.info("Making other Connectors inactive...");
    brokerService.makeConnectorUnavailable(connector_subcontracted.getId());
    LOGGER.info("... Connector inactive.");
    try {
      String query="SELECT ?subject ?predicate ?object WHERE {?subject ?predicate ?object}";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        // Assertation made to fail
        throw new RuntimeException(e.toString());
      }

      String response= "?subject\t?predicate\t?object\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/BaseConnector>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connector-producer>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/securityProfile>\t<https://w3id.org/idsa/code/BASE_SECURITY_PROFILE>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/resourceCatalog>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/curator>\t<https://www.inesctec.pt/>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/description>\t\"Description\"\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/outboundModelVersion>\t\"4.2.6\"\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/maintainer>\t<https://www.inesctec.pt/>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/publicKey>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/hasDefaultEndpoint>\t<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/inboundModelVersion>\t\"4.2.6\"\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637>\t<https://w3id.org/idsa/core/title>\t\"Title\"\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/ConnectorEndpoint>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/connectorEndpoint/dhiasdcna>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1173573807>\t<https://w3id.org/idsa/core/accessURL>\t<https://host.docker.internal:8080/test/connector/api/ids/data>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/PublicKey>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/publicKey/dhiasdcna>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\t<https://w3id.org/idsa/core/keyType>\t<https://w3id.org/idsa/code/RSA>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/-1120545891>\t<https://w3id.org/idsa/core/keyValue>\t\"S2V5VmFsdWVFeGFtcGxl\"\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>\t<https://w3id.org/idsa/core/ResourceCatalog>\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\t<http://www.w3.org/2002/07/owl#sameAs>\t<https://test-broker.com/id/test/catalog/api/connector-producer>\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertTrue(CompareUtil.compareStrings(map.get("payload").toString(), response));
      //assertEquals(map.get("payload"), response);


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_select_useHeaders_CSV() {
    String expectedPayload="subject,predicate,object\n" +
            "http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692,https://w3id.org/idsa/core/title,PDP-P20230303\n" +
            "http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692,https://w3id.org/idsa/core/title,PDP-P20230303\n";
    try {

      LOGGER.info("Creating message Payload");
      // Use a query that only returns a value
      MessagePayload messagePayload=new MessagePayloadInputstream(
              IOUtils.toInputStream(testUtil.createQueryToSearchByTitle().toString()), new ObjectMapper());

      // Set the return type as CSV
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.CSV);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        // Assertation made to fail
        throw new RuntimeException(e.toString());
      }
      // Assert that the payload is the payload expected
      assertEquals(map.get("payload").getClass(), String.class);
      assertTrue(CompareUtil.compareStrings(TestUtil.normalize(map.get("payload").toString()), TestUtil.normalize(expectedPayload)));
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_select_useHeaders_TSV() {
    String expectedPayload="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"\n";
    try {

      LOGGER.info("Creating message Payload");
      // Use a query that only returns a value
      MessagePayload messagePayload=new MessagePayloadInputstream(
              IOUtils.toInputStream(testUtil.createQueryToSearchByTitle().toString()), new ObjectMapper());

      // Set the return type as TSV
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.TSV);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
      // Assert that the payload is the payload expected
      assertEquals(map.get("payload").getClass(), String.class);
      assertTrue(CompareUtil.compareStrings(map.get("payload").toString(), expectedPayload));
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_select_useHeaders_JSON() {
    String expectedPayload="{ \"head\": {\n" +
            "    \"vars\": [ \"subject\" , \"predicate\" , \"object\" ]\n" +
            "  } ,\n" +
            "  \"results\": {\n" +
            "    \"bindings\": [\n" +
            "      { \n" +
            "        \"subject\": { \"type\": \"uri\" , \"value\": \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692\" } ,\n" +
            "        \"predicate\": { \"type\": \"uri\" , \"value\": \"https://w3id.org/idsa/core/title\" } ,\n" +
            "        \"object\": { \"type\": \"literal\" , \"value\": \"PDP-P20230303\" }\n" +
            "      } ,\n" +
            "      { \n" +
            "        \"subject\": { \"type\": \"uri\" , \"value\": \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692\" } ,\n" +
            "        \"predicate\": { \"type\": \"uri\" , \"value\": \"https://w3id.org/idsa/core/title\" } ,\n" +
            "        \"object\": { \"type\": \"literal\" , \"value\": \"PDP-P20230303\" }\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}\n";
    try {

      LOGGER.info("Creating message Payload");
      // Use a query that only returns a value
      MessagePayload messagePayload=new MessagePayloadInputstream(
              IOUtils.toInputStream(testUtil.createQueryToSearchByTitle().toString()), new ObjectMapper());

      // Set the return type as JSON
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.JSON);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
      // Assert that the payload is the payload expected
      assertEquals(map.get("payload").getClass(), String.class);
      assertTrue(CompareUtil.compareStrings(map.get("payload").toString(), expectedPayload));
     // assertEquals(TestUtil.normalize(map.get("payload").toString()), TestUtil.normalize(expectedPayload));

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_select_useHeaders_Text() {
    String expectedPayload="----------------------------------------------------------------------------------------------------------------------------------------------------------\n" +
        "| subject                                                                                         | predicate                          | object          |\n" +
        "==========================================================================================================================================================\n" +
        "| <http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>      | <https://w3id.org/idsa/core/title> | \"PDP-P20230303\" |\n" +
        "| <http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692> | <https://w3id.org/idsa/core/title> | \"PDP-P20230303\" |\n" +
        "----------------------------------------------------------------------------------------------------------------------------------------------------------\n";
    try {

      LOGGER.info("Creating message Payload");
      // Use a query that only returns a value
      MessagePayload messagePayload=new MessagePayloadInputstream(
              IOUtils.toInputStream(testUtil.createQueryToSearchByTitle().toString()), new ObjectMapper());

      // Set the return type as TEXT
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.TEXT);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
      // Assert that the payload is the payload expected
      assertEquals(map.get("payload").getClass(), String.class);
      assertTrue(CompareUtil.compareStrings(map.get("payload").toString(), expectedPayload));
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  // If the header is invalid, it uses the default header
  @Test
  void handleMessage_select_useHeaders_InvalidHeader() {
    String expectedPayload="?subject\t?predicate\t?object\n" +
            "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"\n" +
            "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\t<https://w3id.org/idsa/core/title>\t\"PDP-P20230303\"\n";

    try {

      LOGGER.info("Creating message Payload");
      // Use a query that only returns a value
      MessagePayload messagePayload=new MessagePayloadInputstream(
              IOUtils.toInputStream(testUtil.createQueryToSearchByTitle().toString()), new ObjectMapper());

      // Set the return type as an invalid value
      message.setProperty("https://w3id.org/idsa/core/return-format", "invalid");

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      System.out.println(map.get("payload"));
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
      // Assert that the payload is the payload expected
      assertEquals(map.get("payload").getClass(), String.class);
      assertTrue(CompareUtil.compareStrings(map.get("payload").toString(), expectedPayload));
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_select_invalid_query() {
    try {

      LOGGER.info("Creating message Payload");
      // Use a query that only returns a value
      MessagePayload messagePayload=new MessagePayloadInputstream(
              IOUtils.toInputStream("invalid query"), new ObjectMapper());

      // Set the return type as TSV
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.TSV);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), RejectionMessage.class);
        assertInstanceOf(RejectionMessage.class, requestHeader);
        Assertions.assertTrue(RejectionMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ErrorResponse. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getRejectionReason()).isEqualTo(RejectionReason.BAD_PARAMETERS);
        assertThat(requestHeader.getTransferContract()).isNull();

        assertThat(map.get(PAYLOAD)).isEqualTo("Error parsing query. The query is invalid.");
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }
    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_describe() {
    try {
      String query="PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids: <https://w3id.org/idsa/core/> \n" +
              "DESCRIBE ?x\n" +
              "WHERE  {  ?x owl:sameAs <"+connector_producer.getResourceCatalog().get(0).getId()+"> }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      String response= "@prefix ids: <https://w3id.org/idsa/core/> .\n" +
              "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n" +
              "\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664>\n" +
              "        a                    ids:ResourceCatalog ;\n" +
              "        owl:sameAs           <https://test-broker.com/id/test/catalog/api/connector-producer> ;\n" +
              "        ids:offeredResource  <http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692> .\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertEquals(TestUtil.normalize(map.get("payload").toString()), TestUtil.normalize(response));


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_ask() {
    try {
      String query="PREFIX owl: <http://www.w3.org/2002/07/owl#> PREFIX ids:   <https://w3id.org/idsa/core/>\n" +
              "PREFIX idsc:  <https://w3id.org/idsa/code/>\n" +
              "ASK WHERE  {  ?x owl:sameAs <"+connector_producer.getId()+"> }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      assertEquals(map.get("payload").getClass(), String.class);

      assertTrue(map.get("payload").equals("true"));


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_construct() {
    try {
      String query="PREFIX ids: <https://w3id.org/idsa/core/>" +
              "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject a ids:Resource. \n" +
              "            ?subject ?predicate ?object \n" +
              "            FILTER (regex(str(?predicate), 'title','i')) }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      String response= "{\n" +
              "  \"@graph\" : [ {\n" +
              "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692\",\n" +
              "    \"title\" : \"PDP-P20230303\"\n" +
              "  }, {\n" +
              "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692\",\n" +
              "    \"title\" : \"PDP-P20230303\"\n" +
              "  } ],\n" +
              "  \"@context\" : {\n" +
              "    \"title\" : {\n" +
              "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
              "    },\n" +
              "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
              "  }\n" +
              "}\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertEquals(map.get("payload").toString().replaceAll("\\R", "\n"), response.replaceAll("\\R", "\n"));


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_construct_withHeader_JSONLD() {
    try {
      String query="PREFIX ids: <https://w3id.org/idsa/core/>" +
              "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject a ids:Resource. \n" +
              "            ?subject ?predicate ?object \n" +
              "            FILTER (regex(str(?predicate), 'title','i')) }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      // Add header to message
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.JSON_LD);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      String response= "{\n" +
              "  \"@graph\" : [ {\n" +
              "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692\",\n" +
              "    \"title\" : \"PDP-P20230303\"\n" +
              "  }, {\n" +
              "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692\",\n" +
              "    \"title\" : \"PDP-P20230303\"\n" +
              "  } ],\n" +
              "  \"@context\" : {\n" +
              "    \"title\" : {\n" +
              "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
              "    },\n" +
              "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
              "  }\n" +
              "}\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertEquals(map.get("payload").toString().replaceAll("\\R", "\n"), response.replaceAll("\\R", "\n"));

    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_construct_withHeader_JSON() {
    try {
      String query="PREFIX ids: <https://w3id.org/idsa/core/>" +
              "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject a ids:Resource. \n" +
              "            ?subject ?predicate ?object \n" +
              "            FILTER (regex(str(?predicate), 'title','i')) }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      // Add header to message
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.JSON);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      String response= "{\n" +
              "  \"@graph\" : [ {\n" +
              "    \"@id\" : \"http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692\",\n" +
              "    \"title\" : \"PDP-P20230303\"\n" +
              "  }, {\n" +
              "    \"@id\" : \"http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692\",\n" +
              "    \"title\" : \"PDP-P20230303\"\n" +
              "  } ],\n" +
              "  \"@context\" : {\n" +
              "    \"title\" : {\n" +
              "      \"@id\" : \"https://w3id.org/idsa/core/title\"\n" +
              "    },\n" +
              "    \"ids\" : \"https://w3id.org/idsa/core/\"\n" +
              "  }\n" +
              "}\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertEquals(map.get("payload").toString().replaceAll("\\R", "\n"), response.replaceAll("\\R", "\n"));


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_construct_withHeader_TURTLE() {
    try {
      String query="PREFIX ids: <https://w3id.org/idsa/core/>" +
              "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject a ids:Resource. \n" +
              "            ?subject ?predicate ?object \n" +
              "            FILTER (regex(str(?predicate), 'title','i')) }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      // Add header to message
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.TURTLE);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        Assertions.assertTrue(ResultMessage.class.isAssignableFrom(requestHeader.getClass()),
                "Method should return ResultMessage. It returns "+ requestHeader.getClass());
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      String response= "@prefix ids: <https://w3id.org/idsa/core/> .\n" +
              "\n" +
              "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692>\n" +
              "        ids:title  \"PDP-P20230303\" .\n" +
              "\n" +
              "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692>\n" +
              "        ids:title  \"PDP-P20230303\" .\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertThat(CompareUtil.compareStrings(map.get("payload").toString(), response)).isTrue();
      //assertEquals(map.get("payload"), response);


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleMessage_construct_withHeader_TRIPLES() {
    try {
      String query="PREFIX ids: <https://w3id.org/idsa/core/>" +
              "CONSTRUCT { ?subject ?predicate ?object} WHERE { ?subject a ids:Resource. \n" +
              "            ?subject ?predicate ?object \n" +
              "            FILTER (regex(str(?predicate), 'title','i')) }";

      LOGGER.info("Creating maessage Payload");
      MessagePayload messagePayload=new MessagePayloadInputstream(IOUtils.toInputStream(query), new ObjectMapper());

      // Add header to message
      message.setProperty("https://w3id.org/idsa/core/return-format", ResponseType.TRIPLES);

      LOGGER.info("Calling Handler...");
      MessageResponse messageResponse=handler.handleMessage((QueryMessageImpl) message,messagePayload);
      LOGGER.info("... Handler was executed.");

      map=messageResponse.createMultipartMap(serializer);
      try {
        final var requestHeader = serializer.deserialize((String) map.get("header"), ResultMessage.class);
        assertInstanceOf(ResultMessage.class, requestHeader);
        assertThat(requestHeader.getSecurityToken()).isNotNull();
        assertThat(requestHeader.getModelVersion()).isEqualTo(broker.getOutboundModelVersion());
        assertThat(requestHeader.getCorrelationMessage()).isEqualTo(message.getId());
        assertThat(requestHeader.getIssuerConnector()).isEqualTo(broker.getId());
        assertThat(requestHeader.getSenderAgent()).isEqualTo(broker.getId());
        assertThat(requestHeader.getIssued()).isNotNull();
        assertThat(requestHeader.getTransferContract()).isNull();
      }
      catch (IOException e){
        throw new RuntimeException(e.toString());
      }

      System.out.println(map.get("payload"));
      String response= "<http://test-hostname:8082/connectors/connector-producer/-846787637/1793141664/-435574692> <https://w3id.org/idsa/core/title> \"PDP-P20230303\" .\n" +
          "<http://test-hostname:8082/connectors/connector-subcontracted/-821347780/-878397561/-435574692> <https://w3id.org/idsa/core/title> \"PDP-P20230303\" .\n";

      assertEquals(map.get("payload").getClass(), String.class);

      assertEquals(map.get("payload"), response);


    } catch (SerializeException e) {
      throw new RuntimeException(e);
    }
  }

}