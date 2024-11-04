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
package org.openrewrite.java.dependencies.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class FindMinimumDependencyVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMinimumDependencyVersion("com.fasterxml.jackson*", "jackson-core", "2.14-2.16"));
    }

    @Test
    void minimumMaven() {
        rewriteRun(
          mavenProject(
            "core",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.openrewrite</groupId>
                  <artifactId>core</artifactId>
                  <version>0.1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>2.14.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.15.0</version>
                      </dependency>
                  </dependencies>
                </project>
                """,
              """
                <!--~~(com.fasterxml.jackson.core:jackson-core:2.14.0)~~>--><project>
                  <groupId>org.openrewrite</groupId>
                  <artifactId>core</artifactId>
                  <version>0.1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>2.14.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.15.0</version>
                      </dependency>
                  </dependencies>
                </project>
                """
            )
          ),
          mavenProject(
            "server",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.openrewrite</groupId>
                  <artifactId>server</artifactId>
                  <version>0.1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>2.15.0</version>
                      </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void minimumGradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject(
            "core",
            //language=groovy
            buildGradle(
              """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.0'
                }
                """,
              """
                /*~~(com.fasterxml.jackson.core:jackson-core:2.14.0)~~>*/plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.0'
                }
                """
            )
          ),
          mavenProject(
            "server",
            //language=groovy
            buildGradle(
              """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-core:2.16.1'
                }
                """
            )
          )
        );
    }

    @Test
    void noMatchBecauseVersionIsOutsideRange() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.openrewrite</groupId>
                <artifactId>core</artifactId>
                <version>0.1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-core</artifactId>
                        <version>2.11.0</version>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void findMultiple() {
        rewriteRun(
          spec -> spec.recipeFromYaml("""
            type: specs.openrewrite.org/v1beta/recipe
            name: org.openrewrite.MyRecipe
            description: composite recipe finding 2 versions.
            recipeList:
              - org.openrewrite.java.dependencies.search.FindMinimumDependencyVersion:
                  groupIdPattern: com.fasterxml.jackson.core
                  artifactIdPattern: jackson-core
                  version: 2.14-2.16
              - org.openrewrite.java.dependencies.search.FindMinimumDependencyVersion:
                  groupIdPattern: commons-lang
                  artifactIdPattern: commons-lang
                  version: 2.5-2.7
            """, "org.openrewrite.MyRecipe"),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.openrewrite</groupId>
                <artifactId>core</artifactId>
                <version>0.1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-core</artifactId>
                        <version>2.15.0</version>
                    </dependency>
                    <dependency>
                        <groupId>commons-lang</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <!--~~(com.fasterxml.jackson.core:jackson-core:2.15.0)~~>--><!--~~(commons-lang:commons-lang:2.6)~~>--><project>
                <groupId>org.openrewrite</groupId>
                <artifactId>core</artifactId>
                <version>0.1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-core</artifactId>
                        <version>2.15.0</version>
                    </dependency>
                    <dependency>
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
}
