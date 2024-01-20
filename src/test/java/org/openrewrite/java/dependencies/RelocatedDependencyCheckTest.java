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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.Accumulator;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.GroupArtifact;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.Relocation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class RelocatedDependencyCheckTest implements RewriteTest {
    @Test
    void initialValueParser() {
        Accumulator initialValue = new RelocatedDependencyCheck().getInitialValue(new InMemoryExecutionContext());
        Map<GroupArtifact, Relocation> migrations = initialValue.getMigrations();
        assertThat(migrations)
          .containsEntry(new GroupArtifact("commons-lang", "commons-lang"),
            new Relocation(new GroupArtifact("org.apache.commons", "commons-lang3"), null))
          .containsEntry(new GroupArtifact("org.codehaus.groovy", null),
            new Relocation(new GroupArtifact("org.apache.groovy", null), null));
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RelocatedDependencyCheck());
    }

    @Nested
    class Maven {
        @Test
        @DocumentExample
        void findRelocatedMavenDependencies() {
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
                      <dependency>
                        <!--Relocated to org.apache.commons:commons-lang3-->
                        <groupId>commons-lang</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
                      </dependency>
                      <dependency>
                        <!--Relocated to org.apache.groovy-->
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
                          <plugin>
                            <!--Relocated to org.apache.groovy-->
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
        @Disabled("Not supported yet")
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
                              <dependency>
                                <!--Relocated to org.apache.commons:commons-lang3-->
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

}