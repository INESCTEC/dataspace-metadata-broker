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
package pt.inesctec.ids.broker.converter;

import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;
import pt.inesctec.ids.broker.dto.ResponseMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service @NoArgsConstructor
public class MultipartConverter implements Converter<HttpEntity, ResponseMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultipartConverter.class);


    @Override
    public ResponseMessage convert(HttpEntity httpEntity) {
        ResponseMessage responseMessage =new ResponseMessage(convert2Map(httpEntity));
        //System.out.println(messageResponse.toString());
        return responseMessage;
    }

    public Map<String, Object> convert2Map(HttpEntity httpEntity){
        Map<String, Object> multipartMap =new HashMap<>();
        try {
            String content = EntityUtils.toString(httpEntity);
            multipartMap = parseMultipartContent(content);

            //multipartMap.forEach((key, value) -> System.out.println(key + ": " + value));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return multipartMap;
    }

    public Map<String, Object> convert2Map(byte[] bytes){
        Map<String, Object> multipartMap =new HashMap<>();
        String content = IOUtils.toString(bytes);
        multipartMap = convert2Map(content);
        return multipartMap;
    }

    public Map<String, Object> convert2Map(String string){
        Map<String, Object> multipartMap =parseMultipartContent(string);
        //multipartMap.forEach((key, value) -> System.out.println(key + ": " + value));
        return multipartMap;
    }

    public static Map<String, Object> parseMultipartContent(String content) {
        Map<String, Object> multipartMap = new HashMap<>();

        // Split httpEntity content into parts based on boundary
        List<String> parts = Arrays.stream(content.split("--")).toList();

        // For each part (header and payload) get the value
        parts.forEach(part ->{
            part = part.trim();
            if (!part.isEmpty()) {
                // Split the part into lines
                String[] lines = part.split("\r\n");

                // Extract header name and value
                String headerName = null;
                StringBuilder headerValueBuilder = new StringBuilder();
                boolean isHeader = true;

                for (String line : lines) {
                    if (line.startsWith("Content-Disposition")) {
                        // Extract header name from Content-Disposition line. Header name should always be 'header' and 'payload'.
                        // However, this method does not validate the name. Gets all possible values
                        headerName = line.split(":")[1].trim().split(";")[1].split("=")[1].replace("\"", "");
                    } else if (line.isEmpty()) {
                        // Mark end of header. This differentiates between the 'header' and the message itself sinc there is a empty line to separe them
                        isHeader = false;
                    } else if (!isHeader) {
                        // Append payload of the message
                        headerValueBuilder.append(line).append("\n");
                    }
                }

                // Add header and payload to the map
                if (headerName != null) {
                    multipartMap.put(headerName, headerValueBuilder.toString().trim());
                }
            }
        });
        //System.out.println(multipartMap.toString());
        return multipartMap;
    }

}
