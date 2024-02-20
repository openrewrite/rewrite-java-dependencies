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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class AddDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("guava"));
    }

    @Language("java")
    private final String usingGuavaIntMath = """
          import com.google.common.math.IntMath;
          public class A {
              boolean getMap() {
                  return IntMath.isPrime(5);
              }
          }
      """;

    @DocumentExample("Add Gradle dependency with OnlyIfUsing test scope")
    @ParameterizedTest
    @ValueSource(strings = {"com.google.common.math.*", "com.google.common.math.IntMath"})
    void addGradleDependencyWithOnlyIfUsingTestScope(String onlyIfUsing) {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()).recipe(addDependency("com.google.guava:guava:29.0-jre", onlyIfUsing, null)),
          mavenProject("project",
            srcTestJava(
              java(usingGuavaIntMath)
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id "java-library"
                }
                                
                repositories {
                    mavenCentral()
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
                    testImplementation "com.google.guava:guava:29.0-jre"
                }
                """
            )
          )
        );
    }

    @DocumentExample("Add Maven dependency with system scope")
    @Test
    void addMavenDependencyWithSystemScope() {
        rewriteRun(
          spec -> spec
            .recipe(addDependency("doesnotexist:doesnotexist:1", "com.google.common.math.IntMath", "system")),
          mavenProject("project",
            srcMainJava(
              java(usingGuavaIntMath)
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                </project>
                """,
              """
                <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>doesnotexist</groupId>
                            <artifactId>doesnotexist</artifactId>
                            <version>1</version>
                            <scope>system</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }


    private AddDependency addDependency(String gav, String onlyIfUsing, @Nullable String scope) {
        String[] gavParts = gav.split(":");
        return new AddDependency(
          gavParts[0],
          gavParts[1],
          gavParts.length < 3 ? null : gavParts[2],
          null,
          onlyIfUsing,
          gavParts.length < 4 ? null : gavParts[3],
          null,
          null,
          null,
          scope,
          null,
          null,
          null,
          null
        );
    }
}
