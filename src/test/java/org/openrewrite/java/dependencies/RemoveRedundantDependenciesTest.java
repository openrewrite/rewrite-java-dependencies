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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveRedundantDependenciesTest implements RewriteTest {

    @DocumentExample("Demonstrate the limitation: when a dependency is declared directly, it cannot be detected as redundant")
    @Test
    void noChangeWhenDependencyIsDeclaredDirectly() {
        // jackson-core is declared directly, so it appears at depth=0 in the resolved tree
        // and is not in jackson-databind's transitives. The recipe cannot detect this case.
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
                  "com.fasterxml.jackson.core", "jackson-databind", null, null)),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>

                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>

                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void doNotRemoveWhenVersionsDiffer() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
                  "com.fasterxml.jackson.core", "jackson-databind", null, null)),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>

                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>

                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.16.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void doNotRemoveParentDependency() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
                  "com.fasterxml.jackson.core", "jackson-databind", null, null)),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>

                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>

                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noMatchingParentDependency() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
                  "org.nonexistent", "nonexistent", null, null)),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>

                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>

                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noChangeWhenGradleDependencyIsDeclaredDirectly() {
        // Same limitation as Maven - declared dependencies appear at depth=0
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new RemoveRedundantDependencies(
                    "com.fasterxml.jackson.core", "jackson-databind", null, null)),
          mavenProject("my-app",
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
                    implementation 'com.fasterxml.jackson.core:jackson-core:2.17.0'
                }
                """
            )
          )
        );
    }
}
