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
import org.openrewrite.java.dependencies.table.DependencyListReport;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class DependencyListTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyList(DependencyList.Scope.Compile, true));
    }

    @Test
    void basic() {
        rewriteRun(
            spec -> spec
                .beforeRecipe(withToolingApi())
                .dataTable(DependencyListReport.Row.class, rows -> {
                    assertThat(rows).isNotEmpty();
                    assertThat(rows)
                        .filteredOn(it -> "Maven".equals(it.getBuildTool()) && "rewrite-core".equals(it.getDependencyArtifactId()))
                        .hasSize(1);
                    assertThat(rows)
                        .filteredOn(it -> "Gradle".equals(it.getBuildTool()) && "rewrite-core".equals(it.getDependencyArtifactId()))
                        .hasSize(1);
                }),
            //language=groovy
            buildGradle(
                """
              plugins {
                  id 'java'
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  implementation('org.openrewrite:rewrite-core:7.39.0')
              }
              """),
            //language=xml
            pomXml(
                """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>rewrite-core</artifactId>
                      <version>7.39.0</version>
                  </dependency>
                </dependencies>
              </project>
              """)
        );
    }
}
