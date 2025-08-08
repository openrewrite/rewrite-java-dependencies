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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.dependencies.search.ModuleHasDependency;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

class DependencyInsightTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyInsight("org.springframework*", "*", null, null));
    }

    @DocumentExample
    @Test
    void maven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>5.2.6.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <!--~~>--><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>5.2.6.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

@Nested
class VersionsPatterns {

    final String groupIdPattern = "org.springframework";
    final String artifactIdPattern = "*";
    final @Nullable String scope = null;

    @ParameterizedTest
    @ValueSource(strings = {
      "6.1.5", // exact
      "6.1.1-6.1.15", // hyphenated
      "[6.1.1,6.1.6)", "[6.1.1,6.1.5]", "[6.1.5,6.1.15]", "(6.1.4,6.1.15]", // full range
      "6.1.X", // X range
      "~6.1.0", "~6.1", // tilde range
      "^6.1.0", // caret range
    })
    void inPoms(String versionPattern) {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new DependencyInsight(groupIdPattern, artifactIdPattern, versionPattern, scope)),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>6.1.5</version>
                  </dependency>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-aop</artifactId>
                    <version>6.2.2</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <!--~~>--><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>6.1.5</version>
                  </dependency>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-aop</artifactId>
                    <version>6.2.2</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "6.1.5", // exact
      "6.1.1-6.1.15", // hyphenated
      "[6.1.1,6.1.6)", "[6.1.1,6.1.5]", "[6.1.5,6.1.15]", "(6.1.4,6.1.15]", // full range
      "6.1.X", // X range
      "~6.1.0", "~6.1", // tilde range
      "^6.1.0", // caret range
    })
    void directToMaven(String versionPattern) {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new org.openrewrite.maven.search.DependencyInsight(groupIdPattern, artifactIdPattern, scope, versionPattern, false)),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>6.1.5</version>
                  </dependency>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-aop</artifactId>
                    <version>6.2.2</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <!--~~>--><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>6.1.5</version>
                  </dependency>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-aop</artifactId>
                    <version>6.2.2</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "6.1.5", // exact
      "6.1.1-6.1.15", // hyphenated
      "[6.1.1,6.1.6)", "[6.1.1,6.1.5]", "[6.1.5,6.1.15]", "(6.1.4,6.1.15]", // full range
      "6.1.X", // X range
      "~6.1.0", "~6.1", // tilde range
      "^6.1.0", // caret range
    })
    void inBuildGradle(String versionPattern) {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new DependencyInsight(groupIdPattern, artifactIdPattern, versionPattern, scope)),
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
                  implementation 'org.springframework:spring-core:6.1.5'
                  implementation 'org.springframework:spring-aop:6.2.2'
              }
              """,
            """
              /*~~(Module has dependency: org.springframework:*:%s)~~>*/plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation 'org.springframework:spring-core:6.1.5'
                  implementation 'org.springframework:spring-aop:6.2.2'
              }
              """.formatted(versionPattern)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "6.1.5", // exact
      "6.1.1-6.1.15", // hyphenated
      "[6.1.1,6.1.6)", "[6.1.1,6.1.5]", "[6.1.5,6.1.15]", "(6.1.4,6.1.15]", // full range
      "6.1.X", // X range
      "~6.1.0", "~6.1", // tilde range
      "^6.1.0", // caret range
    })
    void directGradle(String versionPattern) {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new org.openrewrite.gradle.search.DependencyInsight(groupIdPattern, artifactIdPattern, versionPattern, null)),
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
                  implementation 'org.springframework:spring-core:6.1.5'
                  implementation 'org.springframework:spring-aop:6.2.2'
              }
              """,
            """
              /*~~(Module has dependency: org.springframework:*:%s)~~>*/plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation 'org.springframework:spring-core:6.1.5'
                  implementation 'org.springframework:spring-aop:6.2.2'
              }
              """.formatted(versionPattern)
          )
        );
    }
}
}
