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


import com.jayway.jsonpath.DocumentContext;
import de.fraunhofer.iais.eis.DescriptionRequestMessage;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.RequestMessage;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter@Setter
public class Headers extends Serializer implements Serializable {


    private String securitytoken;
    private String modelversion;
    private String issued;
    private String senderagent;
    private String issuerconnector;
    private String correlationmessage;

    private String rejectionReason;

    private String recipientConnector;
    private String recipientAgent;
    private String link;

    // It is a Description RESPONSE Message
    public Headers(DocumentContext ctx, String rejectionReason, String recipientConnector, String recipientAgent, String link){
        this.securitytoken=ctx.read("$.['ids:securityToken']['ids:tokenValue']", String.class);
        this.modelversion=ctx.read("['ids:modelVersion']", String.class);
        this.issued=ctx.read("$.['ids:issued']['@value']", String.class);
        this.senderagent=ctx.read("$.['ids:senderAgent']['@id']", String.class);
        this.issuerconnector=ctx.read("$['ids:issuerConnector']['@id']", String.class);
        this.correlationmessage=ctx.read("$['ids:correlationMessage']['@id']", String.class);
        this.rejectionReason = rejectionReason;
        this.recipientConnector = recipientConnector;
        this.recipientAgent = recipientAgent;
        this.link = link;
    }

    // It is a Description REQUEST Message
    public Headers(DescriptionRequestMessage message, String rejectionReason, String recipientConnector, String recipientAgent, String link){
        this.securitytoken=message.getSecurityToken().getTokenValue();
        this.modelversion=message.getModelVersion();
        this.issued=message.getIssued().toString();
        this.senderagent=message.getSenderAgent().toString();
        this.issuerconnector=message.getIssuerConnector().toString();
        this.correlationmessage=message.getId().toString();
        this.rejectionReason = rejectionReason;
        this.recipientConnector = recipientConnector;
        this.recipientAgent = recipientAgent;
        this.link = link;
    }

    public Headers(Message message, String rejectionReason, String recipientConnector, String recipientAgent, String link){
        this.securitytoken=message.getSecurityToken().getTokenValue();
        this.modelversion=message.getModelVersion();
        this.issued=message.getIssued().toString();
        this.senderagent=message.getSenderAgent().toString();
        this.issuerconnector=message.getIssuerConnector().toString();
        this.correlationmessage=message.getId().toString();
        this.rejectionReason = rejectionReason;
        this.recipientConnector = recipientConnector;
        this.recipientAgent = recipientAgent;
        this.link = link;
    }


    public Map<String, List<String>> toMap(){
        // Get the elementType because the Fraunhoffer Serializer loses the properties 'properties'
        Map<String, List<String>> map = new HashMap<>();
        map.put("ids-securitytoken", List.of(this.securitytoken));
        map.put("ids-modelversion", List.of(this.modelversion));
        map.put("ids-issued", List.of(this.issued));
        map.put("ids-senderagent", List.of(this.senderagent));
        map.put("ids-issuerconnector", List.of(this.issuerconnector));
        map.put("ids-correlationmessage", List.of(this.correlationmessage));


        if(this.rejectionReason!=null) {
            map.put("ids-rejectionReason", List.of(this.rejectionReason));
        }
        if(this.recipientConnector!=null) {
            map.put("ids-recipientconnector", List.of(this.recipientConnector));
        }
        if(this.recipientAgent!=null) {
            map.put("ids-recipientagent", List.of(this.recipientAgent));
        }
        if(this.link!=null) {
            map.put("Link", List.of(this.link));
        }
        return map;
    }

}
