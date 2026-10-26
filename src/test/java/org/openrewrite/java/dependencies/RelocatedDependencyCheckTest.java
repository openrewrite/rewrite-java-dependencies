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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.toml.Assertions.toml;

class RelocatedDependencyCheckTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RelocatedDependencyCheck(null));
    }

    @Test
    void initialValueParser() {
        Accumulator initialValue = new RelocatedDependencyCheck(null).getInitialValue(new InMemoryExecutionContext());
        Map<GroupArtifact, Relocation> migrations = initialValue.getMigrations();
        assertThat(migrations)
          .containsEntry(new GroupArtifact("commons-lang", "commons-lang"),
            new Relocation(new GroupArtifact("org.apache.commons", "commons-lang3"), null, true))
          .containsEntry(new GroupArtifact("org.codehaus.groovy", null),
            new Relocation(new GroupArtifact("org.apache.groovy", null), null, true))
          .containsEntry(new GroupArtifact("com.github.javafaker", "javafaker"),
            new Relocation(new GroupArtifact("net.datafaker", "datafaker"),
              "This library is a modern fork of java-faker, built on Java 8, with up to date libraries and several newly added Fake Generators. See https://github.com/datafaker-net/datafaker/",
              false))
          // Successors that are not relocations
          .doesNotContainKey(new GroupArtifact("com.fasterxml.jackson.core", "jackson-databind"))
          .doesNotContainKey(new GroupArtifact("com.fasterxml.jackson.datatype", null))
          // A relocation to an artifact that is not published
          .doesNotContainKey(new GroupArtifact("org.eclipse.jetty.fcgi", "fcgi-client"))
          // Relocations to prose, or to more than one artifact
          .doesNotContainKey(new GroupArtifact("log4j", "log4j"))
          .doesNotContainKey(new GroupArtifact("org.eclipse.jetty", "jetty-ant"))
          .doesNotContainKey(new GroupArtifact("jca", "jca"));
    }

    @Nested
    class Maven {
        @DocumentExample
        @Test
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
                      <!--~~(Relocated to org.apache.groovy:groovy)~~>--><dependency>
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
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                      <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                        <version>8.0.31</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void changeKeepsVersionProperty() {
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
                    <properties>
                      <mysql.version>8.0.31</mysql.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
                        <version>${mysql.version}</version>
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
                    <properties>
                      <mysql.version>8.0.31</mysql.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                        <version>${mysql.version}</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void changeManagedDependencyAlongWithItsUse() {
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
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>mysql</groupId>
                          <artifactId>mysql-connector-java</artifactId>
                          <version>8.0.31</version>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
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
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>com.mysql</groupId>
                          <artifactId>mysql-connector-j</artifactId>
                          <version>8.0.31</version>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void noMarkerForRelocationToProse() {
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
                        <groupId>log4j</groupId>
                        <artifactId>log4j</artifactId>
                        <version>1.2.17</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void findRelocatedIonJava() {
            rewriteRun(
              recipe -> recipe.dataTable(RelocatedDependencyReport.Row.class, rows -> assertThat(rows).containsExactly(
                new RelocatedDependencyReport.Row("software.amazon.ion", "ion-java", "com.amazon.ion", "ion-java",
                  "From version 1.4.0 onward, Amazon Ion for Java moved to the new group id and Java package name \"com.amazon.ion\". The legacy \"software.amazon.ion\" group id is deprecated. See https://amazon-ion.github.io/ion-docs/news/2020/01/15/software.amazon.ion-deprecated.html")
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
                        <groupId>software.amazon.ion</groupId>
                        <artifactId>ion-java</artifactId>
                        <version>1.5.1</version>
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
                      <!--~~(Relocated to com.amazon.ion:ion-java as per "From version 1.4.0 onward, Amazon Ion for Java moved to the new group id and Java package name "com.amazon.ion". The legacy "software.amazon.ion" group id is deprecated. See https://amazon-ion.github.io/ion-docs/news/2020/01/15/software.amazon.ion-deprecated.html")~~>--><dependency>
                        <groupId>software.amazon.ion</groupId>
                        <artifactId>ion-java</artifactId>
                        <version>1.5.1</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void markWhenRelocatedArtifactDoesNotPublishTheVersion() {
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
                        <groupId>commons-lang</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
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
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void markWhenAnotherDeclarationCanNotMove() {
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
                    <profiles>
                      <profile>
                        <id>legacy</id>
                        <dependencies>
                          <dependency>
                            <groupId>mysql</groupId>
                            <artifactId>mysql-connector-java</artifactId>
                            <version>5.1.49</version>
                          </dependency>
                        </dependencies>
                      </profile>
                    </profiles>
                  </project>
                  """,
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                      <!--~~(Relocated to com.mysql:mysql-connector-j)~~>--><dependency>
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
                        <version>8.0.31</version>
                      </dependency>
                    </dependencies>
                    <profiles>
                      <profile>
                        <id>legacy</id>
                        <dependencies>
                          <!--~~(Relocated to com.mysql:mysql-connector-j)~~>--><dependency>
                            <groupId>mysql</groupId>
                            <artifactId>mysql-connector-java</artifactId>
                            <version>5.1.49</version>
                          </dependency>
                        </dependencies>
                      </profile>
                    </profiles>
                  </project>
                  """
              )
            );
        }

        @Test
        void markUnofficialSuccessorInsteadOfChanging() {
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
                        <groupId>com.github.javafaker</groupId>
                        <artifactId>javafaker</artifactId>
                        <version>1.0.0</version>
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
                      <!--~~(Relocated to net.datafaker:datafaker as per "This library is a modern fork of java-faker, built on Java 8, with up to date libraries and several newly added Fake Generators. See https://github.com/datafaker-net/datafaker/")~~>--><dependency>
                        <groupId>com.github.javafaker</groupId>
                        <artifactId>javafaker</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void markEveryRelocationInAChain() {
            rewriteRun(
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
                        <groupId>javax.servlet</groupId>
                        <artifactId>servlet-api</artifactId>
                        <version>2.5</version>
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
                      <!--~~(Relocated to javax.servlet:javax.servlet-api, then to jakarta.servlet:jakarta.servlet-api as per "Java EE new home is Jakarta EE, see https://wiki.eclipse.org/Jakarta_EE_Maven_Coordinates")~~>--><dependency>
                        <groupId>javax.servlet</groupId>
                        <artifactId>servlet-api</artifactId>
                        <version>2.5</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void changeAsFarAlongAChainAsTheVersionIsPublished() {
            rewriteRun(
              recipe -> recipe.recipe(new RelocatedDependencyCheck(true))
                .cycles(3)
                .expectedCyclesThatMakeChanges(2),
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
                        <groupId>servletapi</groupId>
                        <artifactId>servlet-api</artifactId>
                        <version>2.4</version>
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
                      <!--~~(Relocated to javax.servlet:javax.servlet-api, then to jakarta.servlet:jakarta.servlet-api as per "Java EE new home is Jakarta EE, see https://wiki.eclipse.org/Jakarta_EE_Maven_Coordinates")~~>--><dependency>
                        <groupId>javax.servlet</groupId>
                        <artifactId>servlet-api</artifactId>
                        <version>2.4</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void noMarkerForSuccessorThatIsNotARelocation() {
            rewriteRun(
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
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.17.2</version>
                      </dependency>
                      <dependency>
                        <groupId>com.fasterxml.jackson.datatype</groupId>
                        <artifactId>jackson-datatype-jdk8</artifactId>
                        <version>2.17.2</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void findRelocatedDependencyWithPropertyCoordinates() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.openrewrite.example</groupId>
                    <artifactId>rewrite-example</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <properties>
                      <commons-lang.groupId>commons-lang</commons-lang.groupId>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>${commons-lang.groupId}</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
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
                    <properties>
                      <commons-lang.groupId>commons-lang</commons-lang.groupId>
                    </properties>
                    <dependencies>
                      <!--~~(Relocated to org.apache.commons:commons-lang3)~~>--><dependency>
                        <groupId>${commons-lang.groupId}</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
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
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>sonar-maven-plugin</artifactId>
                            <version>3.0.2</version>
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
                          <!--~~(Relocated to org.sonarsource.scanner.maven:sonar-maven-plugin)~~>--><plugin>
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>sonar-maven-plugin</artifactId>
                            <version>3.0.2</version>
                          </plugin>
                        </plugins>
                      </build>
                  </project>
                  """
              )
            );
        }

        @Test
        void changeRelocatedMavenPlugins() {
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
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>sonar-maven-plugin</artifactId>
                            <version>3.0.2</version>
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
                            <groupId>org.sonarsource.scanner.maven</groupId>
                            <artifactId>sonar-maven-plugin</artifactId>
                            <version>3.0.2</version>
                          </plugin>
                        </plugins>
                      </build>
                  </project>
                  """
              )
            );
        }

        @Test
        void noMarkerForGroupRelocationTheArtifactDidNotFollow() {
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

        @Test
        void markRelocatedParent() {
            rewriteRun(
              recipe -> recipe.recipe(new RelocatedDependencyCheck(true)),
              //language=xml
              pomXml(
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                      <groupId>org.truth0</groupId>
                      <artifactId>truth-parent</artifactId>
                      <version>0.23</version>
                    </parent>
                    <artifactId>rewrite-example</artifactId>
                  </project>
                  """,
                """
                  <project>
                    <modelVersion>4.0.0</modelVersion>
                    <!--~~(Relocated to com.google.truth:truth-parent)~~>--><parent>
                      <groupId>org.truth0</groupId>
                      <artifactId>truth-parent</artifactId>
                      <version>0.23</version>
                    </parent>
                    <artifactId>rewrite-example</artifactId>
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
                new RelocatedDependencyReport.Row("org.codehaus.groovy", "groovy-all", "org.apache.groovy", "groovy-all", null),
                new RelocatedDependencyReport.Row("org.codehaus.groovy", "groovy-bom", "org.apache.groovy", "groovy-bom", null)
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
                      implementation platform("org.codehaus.groovy:groovy-bom:3.0.25")
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
                      /*~~(Relocated to org.apache.groovy:groovy-all)~~>*/implementation "org.codehaus.groovy:groovy-all:${groovyVersion}"
                      /*~~(Relocated to org.apache.groovy:groovy-bom)~~>*/implementation platform("org.codehaus.groovy:groovy-bom:3.0.25")
                  }
                  """
              )
            );
        }

        @Test
        void findRelocatedKotlinDslDependencies() {
            rewriteRun(
              recipe -> recipe.beforeRecipe(withToolingApi()),
              //language=kotlin
              buildGradleKts(
                """
                  plugins {
                      `java-library`
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      implementation("commons-lang:commons-lang:2.6")
                  }
                  """,
                """
                  plugins {
                      `java-library`
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      /*~~(Relocated to org.apache.commons:commons-lang3)~~>*/implementation("commons-lang:commons-lang:2.6")
                  }
                  """
              )
            );
        }

        @Test
        void interpolatedArtifactId() {
            rewriteRun(
              //language=groovy
              buildGradle(
                """
                  plugins {
                      id "java-library"
                  }
                  def name = "commons-lang"
                  dependencies {
                      implementation "commons-lang:${name}:2.6"
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
                """
                  plugins {
                      id "java-library"
                  }
                  repositories {
                      mavenCentral()
                  }
                  dependencies {
                      implementation "com.mysql:mysql-connector-j:8.0.31"
                  }
                  """
              )
            );
        }

        @Test
        void findRelocatedVersionCatalogEntries() {
            rewriteRun(
              toml(
                """
                  [versions]
                  commons-lang = "2.6"

                  [libraries]
                  commons-lang = { module = "commons-lang:commons-lang", version.ref = "commons-lang" }
                  """,
                """
                  [versions]
                  commons-lang = "2.6"

                  [libraries]
                  ~~(Relocated to org.apache.commons:commons-lang3)~~>commons-lang = { module = "commons-lang:commons-lang", version.ref = "commons-lang" }
                  """,
                spec -> spec.path("gradle/libs.versions.toml")
              )
            );
        }

        @Test
        void changeRelocatedVersionCatalogEntries() {
            rewriteRun(
              recipe -> recipe.recipe(new RelocatedDependencyCheck(true)),
              toml(
                """
                  [versions]
                  mysql = "8.0.31"

                  [libraries]
                  mysql = { module = "mysql:mysql-connector-java", version.ref = "mysql" }
                  """,
                """
                  [versions]
                  mysql = "8.0.31"

                  [libraries]
                  mysql = { module = "com.mysql:mysql-connector-j", version.ref = "mysql" }
                  """,
                spec -> spec.path("gradle/libs.versions.toml")
              )
            );
        }
    }
}
