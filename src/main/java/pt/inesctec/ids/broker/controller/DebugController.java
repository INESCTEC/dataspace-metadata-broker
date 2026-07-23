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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import pt.inesctec.ids.broker.service.ModelService;
import pt.inesctec.ids.broker.util.FusekiConnectionAccessor;

@RestController
@ApiResponse
@Tag(name = "4. Test", description = "API for Test")
@RequiredArgsConstructor
public class DebugController {


    private final FusekiConnectionAccessor accessor;


    /**
     * Gets connector self-description without catalogs and resources.
     * @return Self-description or error response.
     */
    @SecurityRequirements
    @DeleteMapping(value = {"/clear"}, produces = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Reset the Broker.", responses = {
        @ApiResponse(responseCode = "200", description = "successful operation"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @ResponseBody
    public ResponseEntity<Object> resetBroker() {
        ModelService modelService=new ModelService(accessor);
        modelService.removeAllData();
        return ResponseEntity.ok(modelService.listAllGraphs());
    }
}
