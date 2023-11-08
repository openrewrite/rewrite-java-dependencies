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
import org.openrewrite.java.dependencies.table.RepositoryAccessibilityReport;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

@SuppressWarnings("GroovyAssignabilityCheck")
public class DependencyResolutionDiagnosticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyResolutionDiagnostic());
    }

    @Test
    void gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            // It is a limitation of the tooling API which prevents configuration-granularity error information from being collected.
            // So the GradleDependencyConfigurationErrors table will never be populated in unit tests.
            .dataTable(RepositoryAccessibilityReport.Row.class, rows -> {
                assertThat(rows).hasSize(4);
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://repo.maven.apache.org/maven2", "", "", 200));
                assertThat(rows).filteredOn(row -> row.getUri().startsWith("file:/") && "".equals(row.getExceptionMessage())).hasSize(1);
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://plugins.gradle.org/m2", "", "", 200));
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://nonexistent.moderne.io/maven2", "java.net.UnknownHostException", "nonexistent.moderne.io", null));
            }),
          //language=groovy
          buildGradle("""
            plugins {
                id("java")
            }
            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    url "https://nonexistent.moderne.io/maven2"
                }
            }
            
            dependencies {
                implementation("org.openrewrite.nonexistent:nonexistent:0.0.0")
            }
            """)
        );
    }


    @Test
    void maven() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .dataTable(RepositoryAccessibilityReport.Row.class, rows -> {
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://repo.maven.apache.org/maven2", "", "", 200));
                assertThat(rows).filteredOn(row -> row.getUri().startsWith("file:/") && "".equals(row.getExceptionMessage())).hasSize(1);
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://nonexistent.moderne.io/maven2", "java.net.UnknownHostException", "nonexistent.moderne.io", null)
                );
            }),
            //language=xml
            pomXml("""
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>test</artifactId>
                  <version>0.1.0</version>
                  
                  <repositories>
                      <repository>
                          <id>nonexistent</id>
                          <url>https://nonexistent.moderne.io/maven2</url>
                      </repository>
                   </repositories>
              </project>
              """)
        );
    }
}
