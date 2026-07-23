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

import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ExceptionUtil {

  public static String getFullStackTrace(Throwable e, String... details) {
    StringBuffer res = new StringBuffer();

    res.append("Exception: " + e.toString());
    res.append("\n");
    for (int i = 0; i < details.length; ++i) {
      res.append("ExceptionContext_");
      res.append(i);
      res.append(": ");
      res.append(details[i]);
      res.append("\n");
    }

    // Message
    //res.append(ExceptionUtils.getMessage(e));
    //res.append("\n");

    // StackTrace
    res.append(ExceptionUtils.getStackTrace(e));
    res.append("\n");

    // Message + StackTrace for the chain exception 
    if (e.getCause() != null)
      res.append(getFullStackTrace(e.getCause()));

    return res.toString();
  }

  public static String getHeaderNamesFromException(ConstraintViolationException e){
    // Replace the Header name to be equal to the IDSA-Broker and add 'ids-' to the header names
    return e.getMessages().stream()
        .map(err-> err
            .replace(" must not be null", "")
            .replace("tokenValue", "securitytoken")
            .toLowerCase()
        )
        .filter(hd-> !hd.startsWith("_") )
        .map(err -> "ids-"+err)
        .toList().toString()
        .replace("[", "")
        .replace("]", "");

  }
}
