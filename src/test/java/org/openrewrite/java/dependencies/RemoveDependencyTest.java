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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveDependencyTest implements RewriteTest {

    @DocumentExample("Remove a Gradle dependency")
    @Test
    void removeGradleDependencyUsingStringNotationWithExclusion() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new RemoveDependency("org.springframework.boot", "spring-boot*", null, null, null)),
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
                  implementation("org.springframework.boot:spring-boot-starter-web:2.7.0") {
                      exclude group: "junit"
                  }
                  testImplementation "org.junit.vintage:junit-vintage-engine:5.6.2"
              }
              """,
            """
              plugins {
                  id 'java-library'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation "org.junit.vintage:junit-vintage-engine:5.6.2"
              }
              """
          )
        );
    }

    @Test
    void removeMavenDependency() {
        rewriteRun(
          spec -> spec.recipe(new RemoveDependency("junit", "junit", null, null, null)),
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
                    <groupId>com.google.guava</groupId>
                    <artifactId>guava</artifactId>
                    <version>29.0-jre</version>
                  </dependency>
                  <dependency>
                    <groupId>junit</groupId>
                    <artifactId>junit</artifactId>
                    <version>4.13.1</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>

                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>

                <dependencies>
                  <dependency>
                    <groupId>com.google.guava</groupId>
                    <artifactId>guava</artifactId>
                    <version>29.0-jre</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-java-dependencies/issues/11")
    @Test
    void doNotRemoveIfInUse() {
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion().dependsOn(
              //language=java
              """
                package org.aspectj.lang.annotation;

                import java.lang.annotation.Target;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface Aspect {
                }
                """
            ))
            .recipe(new RemoveDependency("org.aspectj", "aspectjrt", "org.aspectj.lang.annotation.*", null, null)),
          mavenProject("example",
            //language=java
            srcMainJava(
              java(
                """
                  import org.aspectj.lang.annotation.Aspect;
                  @Aspect
                  class MyLoggingInterceptor {
                  }
                  """
              )
            ),
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
                      <groupId>org.aspectj</groupId>
                      <artifactId>aspectjrt</artifactId>
                      <version>1.9.22.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-java-dependencies/issues/11")
    @Test
    void doRemoveIfNotInUse() {
        rewriteRun(
          spec -> spec.recipe(new RemoveDependency("org.aspectj", "aspectjrt", "java.lang.String", null, null)),
          mavenProject("example",
            //language=java
            srcMainJava(
              java(
                """
                  class MyLoggingInterceptor {
                      // Not using String anywhere here; the dependency should be removed
                  }
                  """
              )
            ),
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
                      <groupId>org.aspectj</groupId>
                      <artifactId>aspectjrt</artifactId>
                      <version>1.9.22.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>

                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                </project>
                """
            )
          )
        );
    }
}
