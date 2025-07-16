/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class DoesNotIncludeDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .beforeRecipe(withToolingApi())
          .recipe(new DoesNotIncludeDependency("org.springframework", "spring-beans", false, "compile", "compileClasspath"));
    }

    @DocumentExample
    @Test
    void whenDoesNotIncludeDependencyInCorrectScopeOrConfigurationMarks() {
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
                    <artifactId>spring-beans</artifactId>
                    <version>6.0.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <!--~~>--><project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>
                <dependencies>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-beans</artifactId>
                    <version>6.0.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          ),
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
                    <artifactId>spring-beans</artifactId>
                    <version>6.0.0</version>
                    <scope>compile</scope>
                  </dependency>
                </dependencies>
              </project>
              """
          ),
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
                testImplementation 'org.springframework:spring-beans:6.0.0'
              }
              """,
            """
              /*~~>*/plugins {
                id 'java-library'
              }
              repositories {
                mavenCentral()
              }
              dependencies {
                testImplementation 'org.springframework:spring-beans:6.0.0'
              }
              """
          ),
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
                implementation 'org.springframework:spring-beans:6.0.0'
              }
              """
          )
        );
    }

    @Test
    void whenInapplicableFileTypeDoesNotMark() {
        rewriteRun(
          //language=java
          java(
            """
              class SomeClass {}
              """
          )
        );
    }
}
