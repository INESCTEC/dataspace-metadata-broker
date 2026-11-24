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

import de.fraunhofer.iais.eis.DescriptionRequestMessageBuilder;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ExceptionUtilTest {

  @Test
  void headerNameWithHibernateValidatorWording() {
    var e = new ConstraintViolationException(List.of("_issued must not be null", "issued must not be null"));
    assertThat(ExceptionUtil.getHeaderNamesFromException(e)).isEqualTo("ids-issued");
  }

  @Test
  void headerNameWithApacheBValWording() {
    var e = new ConstraintViolationException(List.of("_tokenValue may not be null", "tokenValue may not be null"));
    assertThat(ExceptionUtil.getHeaderNamesFromException(e)).isEqualTo("ids-securitytoken");
  }

  @Test
  void headerNameFromRealInfomodelValidation() {
    // Uses the javax.validation provider actually on the classpath, like ConnectorController does
    var e = catchThrowableOfType(ConstraintViolationException.class,
        () -> new DescriptionRequestMessageBuilder().build());
    assertThat(e).isNotNull();
    assertThat(ExceptionUtil.getHeaderNamesFromException(e))
        .doesNotContain("null")
        .contains("ids-issued");
  }
}
