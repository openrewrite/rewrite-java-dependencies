/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.java.dependencies.table.LicenseReport;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

public class DependencyLicenseCheckTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyLicenseCheck("runtime", true));
    }

    @Test
    void maven() {
        rewriteRun(
          spec -> spec.dataTable(LicenseReport.Row.class, rows ->
            assertThat(rows.stream().map(LicenseReport.Row::getLicenseType).distinct().sorted())
              .containsExactly("Apache2", "PublicDomain", "Unknown")),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                    <groupId>org.springframework.security</groupId>
                    <artifactId>spring-security-core</artifactId>
                    <version>4.2.13.RELEASE</version>
                  </dependency>
                  <dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j</artifactId>
                    <version>2.13.1</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}
