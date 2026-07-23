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
package pt.inesctec.ids.broker.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_ACCEPTABLE)
public class BadRequestException extends RuntimeException {

    private final String resourceName;
    private String fieldName;
    private String fieldValue;
    private String fieldValue2;
    private String fieldValue3;


    public BadRequestException(String resourceName) {
        super(String.format(resourceName));
        this.resourceName = resourceName;
    }

    public BadRequestException(String resourceName, String fieldName) {
        super(String.format(resourceName, fieldName));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
    }


}
