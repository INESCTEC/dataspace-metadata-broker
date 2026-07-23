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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;

public class XMLGregorianCalendarConverter implements Converter<LocalDateTime, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLGregorianCalendarConverter.class);

    /** Converts a Date to a string. */
    @Override
    public String convert(LocalDateTime date) {
        // Convert LocalDateTime to XMLGregorianCalendar
        XMLGregorianCalendar xmlGregorianCalendar = null;
        try {
            xmlGregorianCalendar = DatatypeFactory
                    .newInstance()
                    .newXMLGregorianCalendar(
                            GregorianCalendar
                                    .from(date.atZone(ZoneId.systemDefault()))
                    );
            return xmlGregorianCalendar.toString();
        }  catch (IllegalArgumentException | NullPointerException ex){
            // Exception that handles the null's
            return null;
        }
        catch (DatatypeConfigurationException e) {
            LOGGER.error("DatatypeConfigurationException:"+e.getMessage());
            throw new RuntimeException(e);
        }catch (Exception exception){
            if(date==null)
                return null;
            LOGGER.error(exception.getMessage());
            return null;
        }
    }

    public XMLGregorianCalendar convertLocalDateTime2XMLGregorianCalendar(LocalDateTime date) {
        // Convert LocalDateTime to XMLGregorianCalendar

        XMLGregorianCalendar xmlGregorianCalendar = null;
        try {
            xmlGregorianCalendar = DatatypeFactory
                    .newInstance()
                    .newXMLGregorianCalendar(
                            GregorianCalendar
                                    .from(date.atZone(ZoneId.systemDefault()))
                    );
            return xmlGregorianCalendar;
        }  catch (IllegalArgumentException | NullPointerException ex){
            // Exception that handles the null's
            return null;
        }
        catch (DatatypeConfigurationException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
        catch (Exception exception){
            LOGGER.error(exception.getMessage());
            return null;
        }
    }

    public XMLGregorianCalendar convertString2XMLGregorianCalendar(String date) {
        // Convert LocalDateTime to XMLGregorianCalendar

        XMLGregorianCalendar xmlGregorianCalendar = null;
        try {
            xmlGregorianCalendar = DatatypeFactory
                    .newInstance()
                    .newXMLGregorianCalendar(
                            GregorianCalendar
                                    .from(convertString2LocalDateTime(date).atZone(ZoneId.systemDefault()))
                    );
            return xmlGregorianCalendar;
        } catch (IllegalArgumentException | NullPointerException ex){
            // Exception that handles the null's
            return null;
        }
        catch (DatatypeConfigurationException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
        catch (Exception exception){
            LOGGER.error(exception.getMessage());
            return null;
        }
    }
    /**
     * Converts a string to a Date.
     *
     * @param string the string.
     * @return Date representation of the string.
     */
    public LocalDateTime convertString2LocalDateTime(String string) {

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(string);
            LocalDateTime ldt = zdt.toLocalDateTime();
            return ldt;
        }catch (Exception exception){
            try {
                return createLocalDateTime(string);
            }
            catch (IllegalArgumentException | NullPointerException ex){
                // Exception that handles the null's
                return null;
            }
            catch (Exception ex){
                LOGGER.error(exception.toString());
                return null;
            }
        }
    }

    private LocalDateTime createLocalDateTime(XMLGregorianCalendar xmlGregorianCalendar, String string) {

        // 1. get new instance of XMLGregorianCalendar
        //XMLGregorianCalendar xmlGregorianCalendar = null;
        try {
            xmlGregorianCalendar = DatatypeFactory
                    .newInstance()
                    .newXMLGregorianCalendar(string);

            return xmlGregorianCalendar
                    .toGregorianCalendar()
                    .toZonedDateTime()
                    .toLocalDateTime();
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles dates with the Z, for instance: 2021-06-30Z
     * */
    private LocalDateTime createLocalDateTime(String string) {

        // 1. get new instance of XMLGregorianCalendar
        //XMLGregorianCalendar xmlGregorianCalendar = null;
        try {
            XMLGregorianCalendar xmlGregorianCalendar = DatatypeFactory
                    .newInstance()
                    .newXMLGregorianCalendar(string);

            return xmlGregorianCalendar
                    .toGregorianCalendar()
                    .toZonedDateTime()
                    .toLocalDateTime();
        } catch (DatatypeConfigurationException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
