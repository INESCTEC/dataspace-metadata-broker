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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.xml.datatype.XMLGregorianCalendar;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class XMLGregorianCalendarConverterTest {

  private XMLGregorianCalendarConverter converter;
  @BeforeEach
  void setUp() {
    converter=new XMLGregorianCalendarConverter();
  }

  /** Converts a Date to a string. */
  @Test
  void convertToDatabaseColumn() {
    LocalDateTime date = LocalDateTime.of(
            LocalDate.of(2021, 06, 30),
            LocalTime.of(12, 23,22));

    String resultDate=converter.convert(date);
    System.out.println(resultDate);
    String expectedDate="2021-06-30T12:23:22.000+01:00";
    assertEquals(resultDate, expectedDate);
  }

  @Test
  void convertToXMLGregorianCalendar_LocalDate() {
    LocalDateTime date = LocalDateTime.of(
            LocalDate.of(2021, 06, 30),
            LocalTime.of(12, 23,22));

    XMLGregorianCalendar resultDate=converter.convertLocalDateTime2XMLGregorianCalendar(date);
    System.out.println(resultDate);
    String expectedDate="2021-06-30T12:23:22.000+01:00";
    assertEquals(resultDate.toString(), expectedDate);
  }

  @Test
  void convertToXMLGregorianCalendar_String() throws ParseException {
    String date="2021-06-30Z";
    String expectedDate = "2021-06-30T00:00:00.000+01:00";

    XMLGregorianCalendar resultDate=converter.convertString2XMLGregorianCalendar(date);
    System.out.println(resultDate);

    assertEquals(resultDate.toString(), expectedDate.toString());
  }

  @Test
  void convertToEntityAttribute() {
    String date="2021-06-30Z";

    LocalDateTime expectedDate = LocalDate.of(2021, 06, 30).atStartOfDay();

    LocalDateTime resultDate=converter.convertString2LocalDateTime(date);
    System.out.println(resultDate);
    assertEquals(resultDate, expectedDate);
  }
}