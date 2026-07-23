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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.annotations.Expose;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import pt.inesctec.ids.broker.builder.MultipartMessageBuilder;
import pt.inesctec.ids.broker.converter.JsonConverter;
import pt.inesctec.ids.broker.exceptions.InvalidValueException;
import pt.inesctec.ids.broker.util.IDSMessageUtil;
import pt.inesctec.ids.broker.util.MessageUtil;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Getter@Setter

public class ResponseMessage extends Serializer implements Serializable {

    // TODO: add payload contentType.
    /* Handling the payload will depend of this type
    Ex: application-json/text*/

    @Expose
    @JsonProperty
    private Object header;
    @Expose @JsonProperty
    private String payload;
    private Charset charset=StandardCharsets.UTF_8;

    public ResponseMessage(Object header, String payload) {
        this.header = header;
        this.payload = payload;
    }

    public ResponseMessage(Object header, byte[] payload) {
        this.header = header;
        this.payload = new String(payload, charset);
    }

    public ResponseMessage(Map<String, Object> multipartMap) {
        try {
            this.header = multipartMap.get("header").toString();
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        }

        // If there is no payload, it will return the Exception
        try {
            this.payload = multipartMap.get("payload").toString();
        }catch (NullPointerException exception){}

    }

    private HttpEntity buildMultipart(){
        return MultipartMessageBuilder.buildMessage(getHeader(), getPayload());
    }

    @NotNull
    public ResponseEntity<Object> createResponseEntity(HttpStatus httpStatus) {

        HttpEntity ent=this.buildMultipart();

        //System.err.println("ent.getContentType(): "+ent.getContentType());

        HttpHeaders httpHeadersToReturn=new HttpHeaders();
        httpHeadersToReturn.setContentType(MediaType.valueOf(ent.getContentType().getValue()));

        return new ResponseEntity<>(IDSMessageUtil.buildByteResponse(ent), httpHeadersToReturn , httpStatus);
    }

    public String toJson(){
        try {
            String json=this.serialize(this);
            return json;
        }  catch (IOException e) {
            throw new InvalidValueException("Clearing House Message", "Exception: "+e.getMessage());
        }
    }

    public String toJson3(){
        try {
            String jsonHeader=this.serializePlainJson(this.header);

            JsonConverter jsonConverter=new JsonConverter();

            String jsonPayload=this.serialize(this.payload);
            return jsonConverter.object2String(this);
        }  catch (IOException e) {
            throw new InvalidValueException("Clearing House Message", "Exception: "+e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String toJson2() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "header=" + header +
                ", payload='" + payload + '\'' +
                ", charset=" + charset +
                "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResponseMessage that)) return false;
        return getHeader().equals(that.getHeader()) && Objects.equals(getPayload(), that.getPayload()) && getCharset().equals(that.getCharset());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHeader(), getPayload(), getCharset());
    }

    @JsonIgnore
    public Charset getCharset() {
        return charset;
    }

    @JsonIgnore
    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
