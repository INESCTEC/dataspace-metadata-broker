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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import pt.inesctec.ids.broker.builder.TriplesBuilder;
import pt.inesctec.ids.broker.dto.Triples;
import pt.inesctec.ids.broker.util.FusekiUtil;

import java.io.*;
import java.util.*;

@Service
@Scope("singleton")
public class FusekiConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FusekiConverter.class);
    //private final StringWriter sw = new StringWriter();

    @Autowired
    public FusekiConverter() {
    }


    public List<Statement> model2ListOfStatements(Model model){
        LOGGER.debug("Converting the Model to a List of Statements");
        return model.listStatements().toList();
    }

    public Model listOfStatements2Model(List<Statement> statements){
        LOGGER.debug("Converting the a List of Statements To a model");
        Model newModel= ModelFactory.createDefaultModel();
        newModel.add(statements);
        return newModel;
    }

    public JSONObject modelToJson(Model model){
        LOGGER.debug("Converting the Model to JSON Format");
        StringWriter sw = new StringWriter();
        RDFDataMgr.write(sw, model, RDFFormat.JSONLD_PRETTY);
        JSONObject test=new JSONObject(sw.toString());
        return test;
    }

    public StringWriter modelToTurleFormat(Model model){
        LOGGER.debug("Converting the Model to Turtle Format");
        if(model.getNsPrefixMap().isEmpty()){
            model.setNsPrefixes(FusekiUtil.addIDSPrefixs());
        }
        // Convert the Model to RDF data using Turtle format
        StringWriter sw = new StringWriter();
        model.write(sw, "TURTLE");
        return sw;
    }


    public String modelToString(Model model){
        LOGGER.debug("Converting the Model to String");
        RDFWriter writer = RDFWriter.create().format(RDFFormat.JSONLD).source(model).build();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writer.output(os);
        return os.toString();
    }

    public String resultSet2Json(ResultSet results) {
        LOGGER.debug("Converting Results To JSON");
        JSONArray jsonArray = new JSONArray();

        while (results.hasNext()) {
            QuerySolution qs = results.nextSolution();
            JSONObject jsonBinding = new JSONObject();

            // Iterate over the variables in the result
            for (String varName : results.getResultVars()) {
                RDFNode node = qs.get(varName);
                String value = node != null ? node.toString() : "";
                jsonBinding.put(varName, value);
            }

            jsonArray.put(jsonBinding);
        }
        return jsonArray.toString(2); // 2 is for pretty-printing
    }

    /**
     * Prints with the type
     * */
    public String resultSet2ByteArray(ResultSet results) {
        LOGGER.debug("Printing all values");
        // write to a ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ResultSetFormatter.outputAsJSON(outputStream, results);

        // Turn that into a String
        return new String(outputStream.toByteArray());
    }

    /**
     * Converts the Model to JSON (All values on the DB)
     * */
    public JSONObject model2Json(Model model) {
        // Iterate through all the statements in the model and convert them to JSON objects
        StmtIterator statements = model.listStatements();
        // Create a JSON array to store the triples
        JSONArray jsonArray = new JSONArray();

        while (statements.hasNext()) {
            Statement statement = statements.nextStatement();
            Resource subject = statement.getSubject();
            String predicate = statement.getPredicate().toString();
            String object = statement.getObject().toString();

            JSONObject jsonTriple = new JSONObject();
            jsonTriple.put("subject", subject.toString());
            jsonTriple.put("predicate", predicate);
            jsonTriple.put("object", object);

            jsonArray.put(jsonTriple);
        }

        // Create a JSON object to hold the JSON array
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("triples", jsonArray);

        return jsonObject;
    }

    /**
     * Only works if printing all 3 things in order
     * */
    public void orderedTriples2Screen(ResultSet results, Query constructedQuery) {
        LOGGER.debug("Printing all values");
        List<Object> queries=new ArrayList<>();

        while (results.hasNext()) {
            QuerySolution soln = results.nextSolution();

            RDFNode subject = soln.get(constructedQuery.getResultVars().get(0)); //subject
            RDFNode predicate = soln.get(constructedQuery.getResultVars().get(1)); //predicate
            RDFNode object = soln.get(constructedQuery.getResultVars().get(2)); //object

            Triples triples =new Triples(subject.toString(), predicate.toString(), object.toString());
            queries.add(triples);

            System.out.println("Subject: " + subject);
            System.out.println("Predicate: " + predicate);
            System.out.println("Object: " + object);

        }

        queries.forEach(System.out::println);
        //queries.forEach(v-> System.out.println( ((QueryResult) v).toJson()));
    }

    /**
     * Is not used!! The JSON cannot be built this way
     * Used as as example on how to obtain the values
     */
    private static String resultSet2QueryResult2JsonArray(ResultSet results, Query constructedQuery) {
        LOGGER.debug("Creating a Json Array");
        List<Object> queries=new ArrayList<>();
        while (results.hasNext()) {
            QuerySolution soln = results.nextSolution();


            System.err.println(constructedQuery.getProjectVars());
            System.err.println(constructedQuery.getQueryPattern());

            RDFNode subject = soln.get(constructedQuery.getResultVars().get(0)); //subject
            RDFNode predicate = soln.get(constructedQuery.getResultVars().get(1)); //predicate
            RDFNode object = soln.get(constructedQuery.getResultVars().get(2)); //object

            Triples triples =new TriplesBuilder()
                    .subject(subject.toString()!=null ? subject.toString() : null )
                    //.subject(subject.toString())
                    .predicate(predicate.toString()!=null ? predicate.toString() : null )
                    .object(object.toString()!=null ? object.toString() : null )
                    .build();
            queries.add(triples);

        }
        JsonConverter jsonConverter = new JsonConverter();
        try {
            String json=jsonConverter.objectList2JsonList(queries).toString();
            LOGGER.debug(json);
            return json;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, String>> tsvTableString2List(String tsv){
        LOGGER.debug("Converting the TSV String to List");

        List<Map<String, String>> connectorsAndGraphs = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(tsv));

        String line;
        try {
            // Read the headers (?subject and ?__reserved)
            String[] headers = reader.readLine()
                    .replaceAll("_?", "")
                    .split("\t");

            while ((line = reader.readLine()) != null) {
                String[] values = line
                        .replaceAll("<", "")
                        .replaceAll(">", "")
                        .split("\t");

                Map<String, String> dataMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    dataMap.put(headers[i], values[i]);
                }
                connectorsAndGraphs.add(dataMap);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return connectorsAndGraphs;
    }

    public Model convertStringsToRDFModel(List<String> stringList) {
        Model model = ModelFactory.createDefaultModel();

        for (String str : stringList) {
            String[] values = str.split(", "); // Splitting the string by comma assuming CSV format
            if (values.length >= 3) {
                Resource subject = ResourceFactory.createResource(values[0].replace("[", ""));
                Property predicate = ResourceFactory.createProperty(values[1]);
                RDFNode object;

                if (values[2].startsWith("http://") || values[2].startsWith("https://")) {
                    // Object is a URI
                    object = ResourceFactory.createResource(values[2]
                            .replace("]", "")
                            .replaceAll("\\\"", ""));
                } else {
                    // Object is a literal
                    object = ResourceFactory.createPlainLiteral(values[2]
                            .replace("]", "")
                            .replaceAll("\\\"", "")
                    );
                }



                Statement statement = ResourceFactory.createStatement(subject, predicate, object);
                model.add(statement);
            }
        }

        return model;
    }
}
