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
package pt.inesctec.ids.broker.util;

import org.apache.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.exceptions.NullValueException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class IDSMessageUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(IDSMessageUtil.class);

    /**
     * Extract the header part from a Map.
     *
     * @param message The ids response message as map.
     * @return The ids header.
     * @throws IllegalArgumentException If the message is null.
     */
    public static String extractHeaderFromMap(final Map<String, Object> message) {
        if(message!=null)
            return message.get("header").toString();
        else throw new NullValueException("value cannot be null.");
    }

    /**
     * Extract the payload part from the ids messaging services response.
     *
     * @param message The ids response message as map.
     * @return The ids payload.
     * @throws IllegalArgumentException If the message is null.
     */
    public static String extractPayloadFromMap(final Map<String, Object> message) {
        if(message!=null)
            return message.get("payload").toString();
        else throw new NullValueException("value cannot be null.");
    }


    public static byte[] buildByteResponse(HttpEntity responseEntity){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            responseEntity.writeTo(out);
            byte[] bytes = out.toByteArray();
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //InputStream inputStream = new ByteArrayInputStream(out.toByteArray());

    }
}
