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

public class DependencyResolutionDiagnosticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyResolutionDiagnostic());
    }

    @Test
    void gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .dataTable(RepositoryAccessibilityReport.Row.class, rows -> {
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://repo.maven.apache.org/maven2", ""));
                assertThat(rows).filteredOn(row -> row.getUri().startsWith("file:/") && "".equals(row.getErrorMessage())).hasSize(1);
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://repo.maven.apache.org/maven2", "")
                );
                assertThat(rows).contains(
                  new RepositoryAccessibilityReport.Row("https://nonexistent.moderne.io/maven2", "No response from repository")
                );
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
            """
            // It is a limitation of the tooling API which prevents configuration-granularity error information from being collected.
            // When run with real Gradle this recipe _should_ produce the following result, included here for documentation.
//            ,"""
//            ~~(Found Gradle dependency configuration which failed to resolve during parsing)~~>plugins {
//                id("java")
//            }
//            repositories {
//                mavenLocal()
//                mavenCentral()
//                maven {
//                    url "https://nonexistent.moderne.io/maven2"
//                }
//            }
//
//            dependencies {
//                implementation("org.openrewrite.nonexistent:nonexistent:0.0.0")
//            }
//            """
          )
        );
    }
}
