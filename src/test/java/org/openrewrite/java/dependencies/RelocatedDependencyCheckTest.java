/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.Accumulator;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.GroupArtifact;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.Relocation;
import org.openrewrite.java.dependencies.table.RelocatedDependencyReport;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class RelocatedDependencyCheckTest implements RewriteTest {
    @Test
    void initialValueParser() {
        Accumulator initialValue = new RelocatedDependencyCheck(null).getInitialValue(new InMemoryExecutionContext());
        Map<GroupArtifact, Relocation> migrations = initialValue.getMigrations();
        assertThat(migrations)
          .containsEntry(new GroupArtifact("commons-lang", "commons-lang"),
            new Relocation(new GroupArtifact("org.apache.commons", "commons-lang3"), null))
          .containsEntry(new GroupArtifact("org.codehaus.groovy", null),
            new Relocation(new GroupArtifact("org.apache.groovy", null), null));
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RelocatedDependencyCheck(null));
    }

    @Nested
    class Maven {
        @Test
        @DocumentExample
        void findRelocatedMavenDependencies() {
            rewriteRun(
              recipe -> recipe.dataTable(RelocatedDependencyReport.Row.class, rows -> assertThat(rows).containsExactly(
                new RelocatedDependencyReport.Row("commons-lang", "commons-lang", "org.apache.commons", "commons-lang3", null),
                new RelocatedDependencyReport.Row("org.codehaus.groovy", "groovy", "org.apache.groovy", "groovy", null)
              )),
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>commons-lang</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
                      </dependency>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>2.5.6</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                      <!--~~(Relocated to org.apache.commons:commons-lang3)~~>--><dependency>
                        <groupId>commons-lang</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
                      </dependency>
                      <!--~~(Relocated to org.apache.groovy)~~>--><dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy</artifactId>
                        <version>2.5.6</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void changeRelocatedMavenDependencies() {
            rewriteRun(
              recipe -> recipe.recipe(new RelocatedDependencyCheck(true)),
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
                        <version>8.0.31</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """,
                spec -> spec.after(actual -> """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                        <version>%s</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """.formatted(Pattern.compile("<version>(.*)</version>")
                  .matcher(requireNonNull(actual)).results().skip(1).findFirst().orElseThrow().group(1))
                )
              )
            );
        }

        @Test
        void findRelocatedMavenPlugins() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.openrewrite.example</groupId>
                      <artifactId>rewrite-example</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-eclipse-compiler</artifactId>
                            <version>3.3.0-01</version>
                          </plugin>
                        </plugins>
                      </build>
                  </project>
                  """,
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.openrewrite.example</groupId>
                      <artifactId>rewrite-example</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <build>
                        <plugins>
                          <!--~~(Relocated to org.apache.groovy)~~>--><plugin>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-eclipse-compiler</artifactId>
                            <version>3.3.0-01</version>
                          </plugin>
                        </plugins>
                      </build>
                  </project>
                  """
              )
            );
        }

        @Test
        void findRelocatedMavenPluginDependency() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.openrewrite.example</groupId>
                      <artifactId>rewrite-example</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.openrewrite.maven</groupId>
                            <artifactId>org.openrewrite.maven</artifactId>
                            <version>5.20.0</version>
                            <dependencies>
                              <dependency>
                                <groupId>commons-lang</groupId>
                                <artifactId>commons-lang</artifactId>
                                <version>2.6</version>
                              </dependency>
                            </dependencies>
                          </plugin>
                        </plugins>
                      </build>
                  </project>
                  """,
                """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.openrewrite.example</groupId>
                      <artifactId>rewrite-example</artifactId>
                      <version>1.0-SNAPSHOT</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.openrewrite.maven</groupId>
                            <artifactId>org.openrewrite.maven</artifactId>
                            <version>5.20.0</version>
                            <dependencies>
                              <!--~~(Relocated to org.apache.commons:commons-lang3)~~>--><dependency>
                                <groupId>commons-lang</groupId>
                                <artifactId>commons-lang</artifactId>
                                <version>2.6</version>
                              </dependency>
                            </dependencies>
                          </plugin>
                        </plugins>
                      </build>
                  </project>
                  """
              )
            );
        }
    }

    @Nested
    class Gradle {
        @Test
        void findRelocatedGradleDependencies() {
            rewriteRun(
              recipe -> recipe.dataTable(RelocatedDependencyReport.Row.class, rows -> assertThat(rows).containsExactly(
                new RelocatedDependencyReport.Row("commons-lang", "commons-lang", "org.apache.commons", "commons-lang3", null),
                new RelocatedDependencyReport.Row("commons-lang", "commons-lang", "org.apache.commons", "commons-lang3", null),
                new RelocatedDependencyReport.Row("org.codehaus.groovy", "groovy-all", "org.apache.groovy", "groovy-all", null)
              )),
              //language=groovy
              buildGradle(
                """
                  plugins {
                      id "java-library"
                  }
                  repositories {
                      mavenCentral()
                  }
                  def groovyVersion = "2.5.6"
                  dependencies {
                      implementation "commons-lang:commons-lang:2.6"
                      implementation group: "commons-lang", name: "commons-lang", version: "2.6"
                      implementation "org.codehaus.groovy:groovy-all:${groovyVersion}"
                  }
                  """,
                """
                  plugins {
                      id "java-library"
                  }
                  repositories {
                      mavenCentral()
                  }
                  def groovyVersion = "2.5.6"
                  dependencies {
                      /*~~(Relocated to org.apache.commons:commons-lang3)~~>*/implementation "commons-lang:commons-lang:2.6"
                      /*~~(Relocated to org.apache.commons:commons-lang3)~~>*/implementation group: "commons-lang", name: "commons-lang", version: "2.6"
                      /*~~(Relocated to org.apache.groovy)~~>*/implementation "org.codehaus.groovy:groovy-all:${groovyVersion}"
                  }
                  """
              )
            );
        }

        @Test
        void changeRelocatedGradleDependencies() {
            rewriteRun(
              recipe -> recipe
                .beforeRecipe(withToolingApi())
                .recipe(new RelocatedDependencyCheck(true)),
              //language=groovy
              buildGradle(
                """
                  plugins {
                      id "java-library"
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      implementation "mysql:mysql-connector-java:8.0.31"
                  }
                  """,
                spec -> spec.after(actual -> """
                  plugins {
                      id "java-library"
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      implementation "%s"
                  }
                  """.formatted(Pattern.compile("com.mysql:mysql-connector-j:[^\"]+")
                  .matcher(requireNonNull(actual)).results().findFirst().orElseThrow().group()))
              )
            );
        }
    }
}