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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.java.dependencies.table.RepositoryAccessibilityReport;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.dependencies.MavenCentralMirror.settingsMirroringOnlyCentral;
import static org.openrewrite.maven.Assertions.pomXml;

// Which URL Maven Central and the Gradle Plugin Portal are reached at depends on the machine the tests run on: where
// CI names a mirror in MAVEN_CENTRAL_MIRROR_URL, the mirror stands in for both. So these tests check the repositories
// they declare themselves by URL, and Central and the plugin portal only by being reachable.
@SuppressWarnings("GroovyAssignabilityCheck")
class DependencyResolutionDiagnosticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyResolutionDiagnostic(null, null, null));
    }

    @DocumentExample
    @Test
    void gradleNoMarker() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id("java")
              }
              """,
            """
              /*~~(build.gradle is a Gradle build file, but it is missing a GradleProject marker.)~~>*/plugins {
                  id("java")
              }
              """
          )
        );
    }

    @Test
    void gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .dataTable(RepositoryAccessibilityReport.Row.class, rows -> {
                assertThat(rows).filteredOn(row -> row.getUri().startsWith("file:/") && "".equals(row.getPingExceptionMessage())).hasSize(1);
                assertThat(rows)
                  .filteredOn(row -> "https://nonexistent.moderne.io/maven2".equals(row.getUri()) && row.getPingHttpCode() == null).hasSize(1);
                // Maven Central and the plugin portal, which are one row where a mirror stands in for both
                assertThat(remote(rows)).hasSizeBetween(1, 2).allSatisfy(DependencyResolutionDiagnosticTest::reachable);
            }),
          //language=groovy
          buildGradle(
            """
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
          )
        );
    }


    @Test
    void gradleNoDefaultRepos() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .dataTable(RepositoryAccessibilityReport.Row.class, rows -> {
                assertThat(rows)
                  .hasSize(2)
                  .contains(
                    new RepositoryAccessibilityReport.Row("https://nonexistent.moderne.io/maven2", "java.net.UnknownHostException", "nonexistent.moderne.io", null, "", ""));
                // The plugin portal, which Gradle adds
                assertThat(remote(rows)).singleElement().satisfies(DependencyResolutionDiagnosticTest::reachable);
            }),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id("java")
              }
              repositories {
                  maven {
                      url "https://nonexistent.moderne.io/maven2"
                  }
              }

              dependencies {
                  implementation("org.openrewrite.nonexistent:nonexistent:0.0.0")
              }
              """
          )
        );
    }

    @Test
    void mavenSettingsWithMirrors() {
        rewriteRun(
          spec -> {
              MavenExecutionContextView ctx = MavenExecutionContextView.view(new InMemoryExecutionContext());
              MavenSettings settings = MavenSettings.parse(new Parser.Input(Path.of("settings.xml"), () -> new ByteArrayInputStream(
                //language=xml
                """
                  <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
                      <mirrors>
                          <mirror>
                              <mirrorOf>*</mirrorOf>
                              <name>mirrored-repo</name>
                              <url>https://nonexistent.moderne.io/maven2</url>
                              <id>repo</id>
                          </mirror>
                      </mirrors>
                  </settings>
                  """.getBytes())), ctx);
              ctx.setMavenSettings(settings);
              spec.beforeRecipe(withToolingApi())
                .dataTable(RepositoryAccessibilityReport.Row.class, rows -> assertThat(rows)
                        .contains(
                  new RepositoryAccessibilityReport.Row("https://nonexistent.moderne.io/maven2", "java.net.UnknownHostException", "nonexistent.moderne.io", null, "", "")
                )
                        .noneMatch(repo -> repo.getUri().contains("https://repo.maven.apache.org/maven2")))
                .executionContext(ctx);
          },
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>test</artifactId>
                  <version>0.1.0</version>
              </project>
              """
          )
        );
    }

    @Test
    void maven() {
        rewriteRun(
          spec -> {
              // CI may inject ~/.m2/settings.xml with `<mirrorOf>*</mirrorOf>` to route Maven through a cache.
              // That rewrites the pom-declared `nonexistent` repo to the mirror URL, so neither Maven Central
              // nor the test's `nonexistent.moderne.io` repo appear in the diagnostic. Use settings of the test's
              // own here, so the recipe sees the pom-declared repositories directly.
              MavenExecutionContextView ctx = MavenExecutionContextView.view(new InMemoryExecutionContext());
              ctx.setMavenSettings(settingsMirroringOnlyCentral(ctx));
              spec
                .executionContext(ctx)
                .dataTable(RepositoryAccessibilityReport.Row.class, rows -> {
                    assertThat(rows).filteredOn(row -> row.getUri().startsWith("file:/") && "".equals(row.getPingExceptionMessage())).hasSize(1);
                    assertThat(rows).contains(
                      new RepositoryAccessibilityReport.Row("https://nonexistent.moderne.io/maven2", "java.net.UnknownHostException", "nonexistent.moderne.io", null, "", "")
                    );
                    // Maven Central, which the recipe adds
                    assertThat(remote(rows)).singleElement().satisfies(DependencyResolutionDiagnosticTest::reachable);
                });
          },
          //language=xml
          pomXml(
            """
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
              """
          )
        );
    }

    @Test
    void dependencyNotFound() {
        rewriteRun(
          spec -> spec.recipe(new DependencyResolutionDiagnostic("org.nonexistent", "nonexistent", "0"))
            .beforeRecipe(withToolingApi())
            .dataTable(RepositoryAccessibilityReport.Row.class, rows ->
              // Maven Central, which is reachable but has no such dependency
              assertThat(rows).anySatisfy(row -> {
                  reachable(row);
                  assertThat(row.getDependencyResolveExceptionType()).isEqualTo("org.openrewrite.maven.MavenDownloadingException");
              })
            ),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id("java")
              }
              repositories {
                  mavenCentral()
              }
              """
          )
        );
    }

    private static List<RepositoryAccessibilityReport.Row> remote(List<RepositoryAccessibilityReport.Row> rows) {
        return rows.stream()
          .filter(row -> !row.getUri().startsWith("file:/") && !row.getUri().contains("nonexistent.moderne.io"))
          .toList();
    }

    private static void reachable(RepositoryAccessibilityReport.Row row) {
        assertThat(row.getPingHttpCode()).as("what %s answered", row.getUri()).isEqualTo(200);
        assertThat(row.getPingExceptionType()).as("what reaching %s threw", row.getUri()).isEmpty();
    }
}
