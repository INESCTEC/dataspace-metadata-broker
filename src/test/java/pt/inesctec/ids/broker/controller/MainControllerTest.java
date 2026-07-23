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
package pt.inesctec.ids.broker.controller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pt.inesctec.ids.broker.converter.JsonConverter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MainControllerTest {

    //This makes the code run without having the project running
    @LocalServerPort
    private String port;

    @Value(value="${spring.security.user.name}")
    private String username;

    @Value(value="${spring.security.user.password}")
    private String password;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getPublicSelfDescription() throws JSONException {
        System.err.println(port);
        ResponseEntity<String> response = this.restTemplate
                .withBasicAuth(username, password)
                .getForEntity("http://localhost:" + port + "/",
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println(response.getBody());

        JSONObject jsonObject=new JSONObject(response.getBody());
        System.out.println(jsonObject.get("@type"));
        assertThat(jsonObject.get("@type")).isEqualTo("ids:Broker");
        assertThat(jsonObject.get("@id")).isEqualTo("https://inesctec.pt/ids/mediator/id/metadata-broker");
    }
}