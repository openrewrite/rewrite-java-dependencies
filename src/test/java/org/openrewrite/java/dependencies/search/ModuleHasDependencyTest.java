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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class ModuleHasDependencyTest implements RewriteTest {
    private final static String PositiveSub = "(Module has dependency: %1$s:%2$s)~~";
    private final static String NegativeSub = "(Module does not have dependency: %1$s:%2$s)~~";
    private final static String JavaMarkerBase = "/*~~%s>*/";
    private final static String JavaMarkerPositiveBase = JavaMarkerBase.formatted(PositiveSub);
    private final static String JavaMarkerNegativeBase = JavaMarkerBase.formatted(NegativeSub);
    private final static String GradleMarkerPositiveBase = JavaMarkerPositiveBase;
    private final static String GradleMarkerNegativeBase = JavaMarkerNegativeBase;
    private final static String MavenMarkerBase = "<!--~~%s>-->";
    private final static String MavenMarkerPositiveBase = MavenMarkerBase.formatted(PositiveSub);
    private final static String MavenMarkerNegativeBase = MavenMarkerBase.formatted(NegativeSub);

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi());
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleHasDirectDependencyMarks(Boolean invertCondition) {
        final String groupId = "org.springframework";
        final String artifactId = "spring-beans";
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(groupId, artifactId, null, null, invertCondition)),
          mavenProject("project-gradle",
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
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            ),
            //language=java
            java(
              """
                public class AGradle {}
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
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
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            ),
            //language=java
            java(
              """
                public class AMaven {}
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            )
          )
        );
    }

    @Test
    void whenModuleHasDirectDependencyButInvertedMarkingDoesNotMark() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency("org.springframework", "spring-beans", null, null, true)),
          mavenProject("project",
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
            ),
            //language=java
            java(
              """
                public class AGradle {}
                """
            )
          ),
          mavenProject("project-maven",
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
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            //language=java
            java(
              """
                public class AMaven {}
                """
            )
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleHasTransitiveDependencyMarks(Boolean invertCondition) {
        final String groupId = "org.springframework";
        final String artifactId = "spring-beans";
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(groupId, artifactId, null, null, invertCondition)),
          mavenProject("project-gradle",
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
                  implementation 'org.springframework.boot:spring-boot-starter-actuator:3.0.0'
                }
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            ),
            //language=java
            java(
              """
                public class AGradle {}
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-actuator</artifactId>
                      <version>3.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            ),
            //language=java
            java(
              """
                public class AMaven {}
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositiveBase.formatted(groupId, artifactId))
                  .actual()
              )
            )
          )
        );
    }

    @Test
    void whenModuleHasTransitiveDependencyButInvertedMarkingDoesNotMark() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency("org.springframework", "spring-beans", null, null, true)),
          mavenProject("project",
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
                  implementation 'org.springframework.boot:spring-boot-starter-actuator:3.0.0'
                }
                """
            ),
            //language=java
            java(
              """
                public class AGradle {}
                """
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-actuator</artifactId>
                      <version>3.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            //language=java
            java(
              """
                public class AMaven {}
                """
            )
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleDoesNotHaveDependencyDoesNotMark(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency("org.springframework", "spring-beans", null, null, invertCondition)),
          mavenProject("project-gradle",
            //language=groovy
            buildGradle(
              """
                plugins {
                  id 'java-library'
                }
                repositories {
                  mavenCentral()
                }
                """
            ),
            //language=java
            java(
              """
                public class AGradle {}
                """
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            ),
            //language=java
            java(
              """
                public class AMaven {}
                """
            )
          )
        );
    }

    @Test
    void whenModuleDoesNotHaveDependencyButInvertedMarkingMarks() {
        final String groupId = "org.springframework";
        final String artifactId = "spring-beans";
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(groupId, artifactId, null, null, true)),
          mavenProject("project",
            //language=groovy
            buildGradle(
              """
                plugins {
                  id 'java-library'
                }
                repositories {
                  mavenCentral()
                }
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarkerNegativeBase.formatted(groupId, artifactId))
                  .actual()
              )
            ),
            //language=java
            java(
              """
                public class AGradle {}
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerNegativeBase.formatted(groupId, artifactId))
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarkerNegativeBase.formatted(groupId, artifactId))
                  .actual()
              )
            ),
            //language=java
            java(
              """
                public class AMaven {}
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerNegativeBase.formatted(groupId, artifactId))
                  .actual()
              )
            )
          )
        );
    }
}
