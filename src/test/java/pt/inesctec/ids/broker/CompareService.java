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
package pt.inesctec.ids.broker;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import pt.inesctec.ids.broker.converter.MultipartConverter;

import java.util.Map;

import static pt.inesctec.ids.broker.config.IDSConstants.HEADER;
import static pt.inesctec.ids.broker.config.IDSConstants.PAYLOAD;

@Service
@SpringBootTest
public class CompareService {

  private String path;
  private MultipartConverter converter;

  public CompareService(@Qualifier("Endpoint") String path) {
    this.path = path;
    this.restTemplate = new TestRestTemplate();
    this.converter=new MultipartConverter();
  }

  private TestRestTemplate restTemplate;

  public ResponseEntity<? extends Object> executePOSTRequest(HttpEntity<MultiValueMap<String, Object>> requestEntity, Class<?> classDto) {
    return restTemplate.exchange(path, HttpMethod.POST, requestEntity, classDto);
  }

  public ResponseEntity<String> executePOSTRequest(HttpEntity<MultiValueMap<String, Object>> requestEntity) {
    ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.POST, requestEntity, String.class);
    System.out.println("Response from Path: "+path);
    System.out.println(response.toString());
    return response;
  }

  public Map<String, Object> convert2Map(ResponseEntity<String> post_response) {
    return converter.convert2Map(post_response.getBody());
  }

  public Object extractPayloadFromResponse(ResponseEntity<String> post_response) {
    return converter.convert2Map(post_response.getBody()).get(PAYLOAD);
  }

  public Object extractHeaderFromResponse(ResponseEntity<String> post_response) {
    return converter.convert2Map(post_response.getBody()).get(HEADER);
  }

}
