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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class ChangeDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpath("junit-jupiter-api", "guava", "jackson-databind", "jackson-core", "lombok"));
    }

    @DocumentExample("Change Gradle dependency")
    @Test
    void changeGradleDependency() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new ChangeDependency("commons-lang", "commons-lang", "org.apache.commons", "commons-lang3", "3.11.x", null, null)),
          buildGradle(
            """
              plugins {
                  id "java-library"
              }
              
              repositories {
                  mavenCentral()
              }
              
              dependencies {
                  implementation "commons-lang:commons-lang:2.6"
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
                  implementation "org.apache.commons:commons-lang3:3.11"
              }
              """
          )
        );
    }

    @DocumentExample("Change Maven dependency")
    @Test
    void changeMavenDependency() {
        rewriteRun(
          spec -> spec.recipe(new ChangeDependency("commons-lang", "commons-lang", "org.apache.commons", "commons-lang3", "3.11.x", null, null)),
          pomXml(
            """
              <project>
                  <groupId>com.example.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
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
                  <groupId>com.example.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.commons</groupId>
                          <artifactId>commons-lang3</artifactId>
                          <version>3.11</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void doNotPinWhenNotVersionedGradle() {
        rewriteRun(
          spec -> spec
            .beforeRecipe(withToolingApi())
            .recipe(new ChangeDependency("mysql", "mysql-connector-java", "com.mysql", "mysql-connector-j", "8.0.x", null, null)),
          buildGradle(
            """
              plugins {
                id 'java'
                id 'org.springframework.boot' version '2.6.1'
                id 'io.spring.dependency-management' version '1.0.11.RELEASE'
              }
              
              repositories {
                 mavenCentral()
              }
              
              dependencies {
                  runtimeOnly 'mysql:mysql-connector-java'
              }
              """,
            """
              plugins {
                id 'java'
                id 'org.springframework.boot' version '2.6.1'
                id 'io.spring.dependency-management' version '1.0.11.RELEASE'
              }
              
              repositories {
                 mavenCentral()
              }
              
              dependencies {
                  runtimeOnly 'com.mysql:mysql-connector-j'
              }
              """)
        );
    }

    @Test
    void pinWhenOverrideManagedVersionGradle() {
        rewriteRun(
          spec -> spec
            .beforeRecipe(withToolingApi())
            .recipe(new ChangeDependency("mysql", "mysql-connector-java", "com.mysql", "mysql-connector-j", "8.0.x", null, true)),
          buildGradle(
            """
              plugins {
                id 'java'
                id 'org.springframework.boot' version '2.6.1'
                id 'io.spring.dependency-management' version '1.0.11.RELEASE'
              }
              
              repositories {
                 mavenCentral()
              }
              
              dependencies {
                  runtimeOnly 'mysql:mysql-connector-java'
              }
              """,
            """
              plugins {
                id 'java'
                id 'org.springframework.boot' version '2.6.1'
                id 'io.spring.dependency-management' version '1.0.11.RELEASE'
              }
              
              repositories {
                 mavenCentral()
              }
              
              dependencies {
                  runtimeOnly 'com.mysql:mysql-connector-j:8.0.33'
              }
              """)
        );
    }
}
