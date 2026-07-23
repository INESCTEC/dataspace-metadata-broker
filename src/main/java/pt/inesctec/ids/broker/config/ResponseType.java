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
package pt.inesctec.ids.broker.config;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.reflect.Modifier.*;

/**
 * The descriptions of the accepted types to return on a query Message
 */
public final class ResponseType {

    /**
     * Response type json.
     */
    public static final String JSON = "application/json";

    /**
     * Response type json-ld.
     */
    public static final String JSON_LD = "application/ld+json";

    /**
     * Response type csv.
     */
    public static final String CSV = "text/csv";

    /**
     * Response type tsv.
     */
    public static final String TSV = "text/tab-separated-values";

    /**
     * Response type text.
     */
    public static final String TEXT = "text/plain";

    /**
     * Response type turtle.
     */
    public static final String TURTLE = "text/turtle";

    /**
     * Response type triples.
     */
    public static final String TRIPLES = "text/triples";

    /**
     * Response type rdf+xml.
     */
    public static final String RDF_XML = "application/rdf+xml";

    /**
     * Response type n-triples.
     */
    public static final String NTRIPLES = "application/n-triples";

    private ResponseType() {
    }

    public static boolean checkIfExists(String value) {
        AtomicBoolean returnValue= new AtomicBoolean(false);
        Field[] fields = ResponseType.class.getDeclaredFields();
        Arrays.stream(fields).forEach( field -> {
            if (isStatic(field.getModifiers())
                    && isPublic(field.getModifiers())
                    && isFinal(field.getModifiers())) {
                try {
                    String fieldValue = (String) field.get(null);
                    if (value.equals(fieldValue)) {
                        returnValue.set(true);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return returnValue.get();
    }
}
