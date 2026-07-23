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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import pt.inesctec.ids.broker.exceptions.BadRequestException;
import pt.inesctec.ids.broker.util.MyJacksonObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@Scope("singleton")
public class JsonConverter {

    private final ObjectMapper objectMapper = new MyJacksonObjectMapper();

    @Autowired
    public JsonConverter() {
        super();
    }

    public ArrayNode newArrayNode() {
        return objectMapper.createArrayNode();
    }

    public ObjectNode newObjectNode() {
        return objectMapper.createObjectNode();
    }

    public ArrayNode newArrayNodeWithJsonNodes(JsonNode... objs) {
        ArrayNode array = newArrayNode();
        for (JsonNode obj : objs)
            array.add(obj);
        return array;
    }

    public JsonNode string2Json(String jsonString) throws Exception {
        return objectMapper.readTree(jsonString);
    }

    public JsonNode file2Json(File jsonFile) throws Exception {
        return objectMapper.readTree(jsonFile);
    }

    public JsonNode file2Json(String fileName) throws Exception {
        try (FileInputStream stream = new FileInputStream(fileName);) {
            return inputStream2Json(stream);
        }
    }

    public String file2String(String fileName) throws Exception {
        return FileUtils.readFileToString(new File(fileName), "UTF-8");
    }

    public byte[] file2ByteArray(String fileName) throws Exception {
        return FileUtils.readFileToByteArray(new File(fileName));
    }

    public JsonNode inputStream2Json(InputStream src) throws Exception {
        return objectMapper.readTree(src);
    }

    public JsonNode object2Json(final Object data) throws Exception {
        return objectMapper.valueToTree(data);
    }

    public String object2String(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    public List<JsonNode> objectList2JsonList(final List<Object> objList) throws Exception {
        List<JsonNode> list = new ArrayList<JsonNode>();
        for (Object obj : objList)
            list.add(object2Json(obj));
        System.err.println(list);
        return list;
    }

    public <T> T json2Object(Class<T> type, JsonNode jsonNode) throws Exception {
        T obj = fromJson(jsonNode, type);
        if (obj != null)
            return obj;
        else
            throw new BadRequestException("Invalid Json data, expecting data to populate " + type);
    }

    @SuppressWarnings("unchecked")
    public <T> T string2Object(Class<T> type, String jsonString) throws Exception {
        if (type == String.class)
            return (T) jsonString;
        else
            return json2Object(type, string2Json(jsonString));
    }

    public <T> List<T> json2ObjectList(Class<T> type, JsonNode jsonNode) throws Exception {
        if (!jsonNode.isArray())
            throw new BadRequestException("Invalid Json data, expecting an array");

        List<T> list = new ArrayList<T>();

        for (JsonNode node : jsonNode) {
            T obj = fromJson(node, type);
            if (obj != null)
                list.add(obj);
            else
                throw new BadRequestException("Invalid Json data, expecting data to populate " + type);
        }

        return list;
    }

    public <A> A fromJson(JsonNode json, Class<A> clazz) throws Exception {
        return objectMapper.treeToValue(json, clazz);
    }


    /* String */
    public JsonNode stringArrayToJson(String[] stringArray) {
        ArrayNode json = newArrayNode();
        for (String name : stringArray)
            json.add(name);
        return json;
    }

    public List<String> json2StringList(JsonNode json) {
        List<String> stringArray = new ArrayList<String>();

        Iterator<JsonNode> it = json.elements();
        while (it.hasNext()) {
            JsonNode e = it.next();
            stringArray.add(e.asText());
        }

        return stringArray;
    }

    public JsonNode stringListToJson(List<String> stringList) {
        ArrayNode json = newArrayNode();
        for (String name : stringList)
            json.add(name);
        return json;
    }

    /* double */
    public JsonNode doubleArrayToJson(double[] doubleArray) {
        ArrayNode json = newArrayNode();
        for (double value : doubleArray)
            json.add(value);
        return json;
    }

    public double[] json2DoubleArray(JsonNode json) {
        double[] doubleArray = new double[json.size()];

        Iterator<JsonNode> it = json.elements();
        int i = 0;
        while (it.hasNext()) {
            JsonNode e = it.next();
            doubleArray[i++] = e.asDouble();
        }

        return doubleArray;
    }

    public JsonNode doubleListToJson(List<Double> doubleList) {
        ArrayNode json = newArrayNode();
        for (double value : doubleList)
            json.add(value);
        return json;
    }

}
