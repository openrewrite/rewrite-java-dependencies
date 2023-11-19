/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class RemoveExpiredSuppressionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveExpiredSuppressions());
    }

    @Test
    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-java-dependencies/issues/24")
    void removeExpiredSuppressions() {
        rewriteRun(
          //language=XML
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
                  <suppress until="2023-05-19Z">
                      <notes><![CDATA[
                      file name: woodstox-core-6.3.1.jar
                      Severity: HIGH
                      False positive. We do not use woodstox and it will be updated with the next spring cloud
                      dependencies.
                  ]]></notes>
                      <packageUrl regex="true">^pkg:maven/com\\.fasterxml\\.woodstox/woodstox\\-core@.*$</packageUrl>
                      <vulnerabilityName>CVE-2022-40152</vulnerabilityName>
                  </suppress>
                  <suppress until="3000-01-01Z">
                      <notes><![CDATA[
                          file name: jackson-databind-2.15.2.jar
                          This is not a really valid CVE and not really exploitable as Java code needs to be modified: https://github.com/FasterXML/jackson-databind/issues/3972
                      ]]></notes>
                      <packageUrl regex="true">^pkg:maven/com\\.fasterxml\\.jackson\\.core/jackson\\-databind@.*$</packageUrl>
                      <cve>CVE-2023-35116</cve>
                  </suppress>
              </suppressions>
              """,
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
                  <suppress until="3000-01-01Z">
                      <notes><![CDATA[
                          file name: jackson-databind-2.15.2.jar
                          This is not a really valid CVE and not really exploitable as Java code needs to be modified: https://github.com/FasterXML/jackson-databind/issues/3972
                      ]]></notes>
                      <packageUrl regex="true">^pkg:maven/com\\.fasterxml\\.jackson\\.core/jackson\\-databind@.*$</packageUrl>
                      <cve>CVE-2023-35116</cve>
                  </suppress>
              </suppressions>
              """
          )
        );
    }
}