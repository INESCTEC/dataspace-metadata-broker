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
package pt.inesctec.ids.broker.builder;

import de.fraunhofer.iais.eis.Message;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static pt.inesctec.ids.broker.config.IDSConstants.HEADER;
import static pt.inesctec.ids.broker.config.IDSConstants.PAYLOAD;


public class MultipartMessageBuilder {
    private final MultipartEntityBuilder builder;

    private MultipartMessageBuilder(Object header){
        if(header==null){
            throw new InvalidValueException("Header Message cannot be null");
        }
        final StringBody headerAsStringBody = new StringBody(header.toString(), ContentType.MULTIPART_FORM_DATA);
        this.builder = MultipartEntityBuilder.create();

        // Set builder values
        builder.setMode(HttpMultipartMode.STRICT);
        //builder.setMode(HttpMultipartMode.LEGACY);
        builder.setBoundary(UUID.randomUUID().toString());
        builder.setCharset(StandardCharsets.UTF_8);
        builder.setContentType(ContentType.APPLICATION_JSON);

        // Add header
        builder.addPart(HEADER, headerAsStringBody);

    }

    private void addPayload(String payload) {
        final StringBody payloadAsStringBuilder = new StringBody(payload, ContentType.MULTIPART_FORM_DATA);
        ByteArrayInputStream payloadStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));

        this.builder.addPart(PAYLOAD, payloadAsStringBuilder);
        //this.builder.addBinaryBody(PAYLOAD, payloadStream);
    }

    private HttpEntity build() {
        return this.builder.build();
    }

    /**
     * Builds a HttpEntity with header and payload
     * @param header the ids Message
     * @param payload the message payload
     * @return HttpEntity
     * */
    public static HttpEntity buildMessage(Object header, String payload) {
        if(payload==null) {
            return MultipartMessageBuilder.buildMessage(header);
        }
        else{
            MultipartMessageBuilder multipartMessageBuilder = new MultipartMessageBuilder(header);
            // Add payload
            multipartMessageBuilder.addPayload(payload);
            // Build Multipart Message
            return multipartMessageBuilder.build();
        }
    }

    /**
     * Builds a HttpEntity with header
     * @param header the ids Message
     * @return HttpEntity
     * */
    public static HttpEntity buildMessage(Object header) {
        MultipartMessageBuilder multipartMessageBuilder = new MultipartMessageBuilder(header);
        // Build Multipart Message
        return multipartMessageBuilder.build();
    }
}
