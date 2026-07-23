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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.inesctec.ids.broker.converter.JsonConverter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompareUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompareUtil.class);


  /**
   * Checks if two lists are the same when one or both are empty or null
   * @return true if they are equal
   * @return false if they are different
   * */
  public static boolean compareListsEmptyAndNull(List<?> list1, List<?> list2){

    // Ideal condition. Already handles all nulls and all empty lists, as well as list content. For more specific cases it uses the other conditions
    if(Objects.equals(list1, list2)) return true;


    //System.out.println("list1: "+ list1 + "\nlist2: " + list2);
    // If one list is null and other is empty, they are considered equal
    try {
      if((list1==null && list2.isEmpty()) || list2==null && list1.isEmpty()) return true;
    }
    catch (NullPointerException ex){}

    if(list1==null) return false;
    if(list2==null) return false;

    return false;
  }

  public static boolean compareStrings(List<String> list1, List<String> list2){

    if(compareListsEmptyAndNull(list1, list2)) return true;

    if(list1==null) return false;
    if(list2==null) return false;

    List<String> constraintList1= new ArrayList<>(list1);
    List<String> constraintList2= new ArrayList<>(list2);

    constraintList1.sort(new SortByString());
    constraintList2.sort(new SortByString());

    /*System.out.println("compareListsByLastName: "+ CollectionUtils.isEqualCollection(constraintList1, constraintList2));
    System.out.println("compareListsByLastName: "+ constraintList1.containsAll(constraintList2));
    System.out.println("compareListsByLastName: "+ constraintList1.equals(constraintList2));
    System.out.println("compareListsByLastName: "+ constraintList1.toString().equals(constraintList2.toString()));
    System.out.println("compareListsByLastName: "+ ListUtils.isEqualList(constraintList1,constraintList2));*/
    if(CollectionUtils.isEqualCollection(constraintList1, constraintList2) ||
            ListUtils.isEqualList(constraintList1,constraintList2) || constraintList1.toString().equals(constraintList2.toString())){
      return true;
    }

    return false;
  }

  public static boolean compareListOfTypedLiterals(List<TypedLiteral> list1, List<TypedLiteral> list2){

    if(compareListsEmptyAndNull(list1, list2)) return true;

    if(list1==null) return false;
    if(list2==null) return false;

    List<TypedLiteral> constraintList1= new ArrayList<>(list1);
    List<TypedLiteral> constraintList2= new ArrayList<>(list2);

    constraintList1.sort(new SortByTypedLiterals());
    constraintList2.sort(new SortByTypedLiterals());

    if(CollectionUtils.isEqualCollection(constraintList1, constraintList2) ||
        ListUtils.isEqualList(constraintList1,constraintList2) || constraintList1.toString().equals(constraintList2.toString())){
      return true;
    }

    return false;
  }

  /**
   * Compares Strings with Multiple Lines
   * */
  public static boolean compareStrings(String string1, String string2){

    ArrayList<String> string1AsList = new ArrayList<>(Arrays.asList(string1.split("\n")));
    ArrayList<String> string2AsList = new ArrayList<>(Arrays.asList(string2.toString().split("\n")));

    /*string1AsList.forEach(System.err::println);
    string2AsList.forEach(System.out::println);*/
    return compareStrings(string1AsList, string2AsList);
  }

  public static boolean compareJsonStrings(String string1, String string2){

    if(
            string1.replaceAll("(.{64})", "$1\n")
                .replaceAll("\n", "")
                .replaceAll(System.lineSeparator(), "")
            .equals(
            string2.replaceAll("(.{64})", "$1\n")
                    .replaceAll("\n", "")
                    .replaceAll(System.lineSeparator(), "")
            ))
      return true;

    JsonConverter jsonConverter=new JsonConverter();

    try {
      JsonNode json1=jsonConverter.string2Json(string1);
      JsonNode json2=jsonConverter.string2Json(string2);

      if(json1.equals(json2)) return true;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }


    return false;
  }

  public static boolean compareJsonNodes(ArrayNode node1, ArrayNode node2) {
    if (node1.size() != node2.size()) {
      return false;
    }

    Set<JsonNode> set1 = toSortedSet(node1);
    Set<JsonNode> set2 = toSortedSet(node2);

    return set1.equals(set2);
  }

  private static Set<JsonNode> toSortedSet(ArrayNode arrayNode) {
    Set<JsonNode> set = new TreeSet<>(Comparator.comparing(JsonNode::toString));
    arrayNode.forEach(set::add);
    return set;
  }

  public static boolean compareStatements(Statement stmt1, Statement stmt2) {
    // Compare subject, predicate, and object
    Resource subj1 = stmt1.getSubject();
    Property pred1 = stmt1.getPredicate();
    String obj1 = stmt1.getObject().toString();

    Resource subj2 = stmt2.getSubject();
    Property pred2 = stmt2.getPredicate();
    String obj2 = stmt2.getObject().toString();

    /*System.out.println("subj1: "+subj1 + " subj2: "+subj2+" Equal: "+subj1.equals(subj2));
    System.out.println("pred1: "+pred1 + " pred2: "+pred2+" Equal: "+pred1.equals(pred2));
    System.out.println("obj1: "+obj1 + " obj2: "+obj2+" Equal: "+obj1.equals(obj2));*/

    return subj1.equals(subj2) && pred1.equals(pred2) && obj1.equals(obj2);
  }



  static class SortByString implements Comparator<String>
  { @Override
  public int compare(String o1, String o2) {
    return o1.compareTo(o2);
  }
  }

  static class SortByTypedLiterals implements Comparator<TypedLiteral>
  {
    @Override
    public int compare(TypedLiteral typedLiteral, TypedLiteral t1) {
      return typedLiteral.getValue().compareTo(t1.getValue());
    }
  }

}
