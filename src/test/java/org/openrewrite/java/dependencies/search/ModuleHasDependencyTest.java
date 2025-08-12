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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
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
    private final static String GroupId = "org.springframework";
    private final static String ArtifactId = "spring-beans";

    private final static String PositiveSub = "(Module has dependency: %1$s:%2$s)~~";
    private final static String NegativeSub = "(Module does not have dependency: %1$s:%2$s)~~";
    private final static String NegativeVacuousSub = "(No module, so vacuously does not have dependency: %1$s:%2$s)~~";
    private final static String JavaMarkerBase = "/*~~%s>*/";
    private final static String JavaMarkerPositive = JavaMarkerBase.formatted(PositiveSub.formatted(GroupId, ArtifactId));
    private final static String JavaMarkerNegative = JavaMarkerBase.formatted(NegativeSub.formatted(GroupId, ArtifactId));
    private final static String JavaMarkerNegativeVacuous = JavaMarkerBase.formatted(NegativeVacuousSub.formatted(GroupId, ArtifactId));
    private final static String GradleMarkerPositive = JavaMarkerPositive;
    private final static String GradleMarkerNegative = JavaMarkerNegative;
    private final static String MavenMarkerBase = "<!--~~%s>-->";
    private final static String MavenMarkerPositive = MavenMarkerBase.formatted(PositiveSub.formatted(GroupId, ArtifactId));
    private final static String MavenMarkerNegative = MavenMarkerBase.formatted(NegativeSub.formatted(GroupId, ArtifactId));

    @Language("groovy")
    private final static String GradleNone = """
      plugins {
        id 'java-library'
      }
      repositories {
        mavenCentral()
      }
      """;

    @Language("xml")
    private final static String MavenNone = """
      <project>
        <groupId>com.example</groupId>
        <artifactId>foo</artifactId>
        <version>1.0.0</version>
      </project>
      """;

    @Language("groovy")
    private final static String GradleDirect = """
      plugins {
        id 'java-library'
      }
      repositories {
        mavenCentral()
      }
      dependencies {
        implementation 'org.springframework:spring-beans:6.0.0'
      }
      """;

    @Language("xml")
    private final static String MavenDirect = """
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
      """;

    @Language("groovy")
    private final static String GradleTransitive = """
      plugins {
        id 'java-library'
      }
      repositories {
        mavenCentral()
      }
      dependencies {
        implementation 'org.springframework.boot:spring-boot-starter-actuator:3.0.0'
      }
      """;

    @Language("xml")
    private final static String MavenTransitive = """
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
      """;


    @Language("java")
    private final static String GradleJava = """
      public class AGradle {}
      """;

    @Language("java")
    private final static String MavenJava = """
      public class AMaven {}
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi());
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenNoModuleDoesNotMark(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, invertCondition)),
          java(GradleJava)
        );
    }

    @Test
    void whenNoModuleButInvertedMarkingMarks() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, true)),
          java(
            GradleJava,
            spec -> spec.after(actual ->
              assertThat(actual)
                .startsWith(JavaMarkerNegativeVacuous)
                .actual()
            )
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleHasDirectDependencyMarks(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, invertCondition)),
          mavenProject("project-gradle",
            buildGradle(
              GradleDirect,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarkerPositive)
                  .actual()
              )
            ),
            java(
              GradleJava,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositive)
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            pomXml(
              MavenDirect,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarkerPositive)
                  .actual()
              )
            ),
            java(
              MavenJava,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositive)
                  .actual()
              )
            )
          )
        );
    }

    @Test
    void whenModuleHasDirectDependencyButInvertedMarkingDoesNotMark() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, true)),
          mavenProject("project",
            buildGradle(GradleDirect),
            java(GradleJava)
          ),
          mavenProject("project-maven",
            pomXml(MavenDirect),
            java(MavenJava)
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleHasTransitiveDependencyMarks(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, invertCondition)),
          mavenProject("project-gradle",
            buildGradle(
              GradleTransitive,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarkerPositive)
                  .actual()
              )
            ),
            java(
              GradleJava,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositive)
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            pomXml(
              MavenTransitive,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarkerPositive)
                  .actual()
              )
            ),
            java(
              MavenJava,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerPositive)
                  .actual()
              )
            )
          )
        );
    }

    @Test
    void whenModuleHasTransitiveDependencyButInvertedMarkingDoesNotMark() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, true)),
          mavenProject("project",
            buildGradle(GradleTransitive),
            java(GradleJava)
          ),
          mavenProject("project-maven",
            pomXml(MavenTransitive),
            java(MavenJava)
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleDoesNotHaveDependencyDoesNotMark(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, invertCondition)),
          mavenProject("project-gradle",
            buildGradle(GradleNone),
            java(GradleJava)
          ),
          mavenProject("project-maven",
            pomXml(MavenNone),
            java(MavenJava)
          )
        );
    }

    @Test
    void whenModuleDoesNotHaveDependencyButInvertedMarkingMarks() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, true)),
          mavenProject("project",
            buildGradle(
              GradleNone,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GradleMarkerNegative)
                  .actual()
              )
            ),
            java(
              GradleJava,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerNegative)
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            pomXml(
              MavenNone,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MavenMarkerNegative)
                  .actual()
              )
            ),
            java(
              MavenJava,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JavaMarkerNegative)
                  .actual()
              )
            )
          )
        );
    }

@Nested
class WithVersionsPattern {
    @ParameterizedTest
    @ValueSource(strings = {
      "1.0.1", // exact
      "1.0.1-1.0.5", // hyphenated
      "[1.0.1,1.0.5)", "[1.0.1,1.0.5]", "[1.0.1,1.0.5]", "(1.0.0,1.0.5]", // full range
      "~1.0.1"// tilde range
    })
    void maven(String versionPattern) {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new ModuleHasDependency("jakarta.data", "*", null, versionPattern, null)),
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
                      <groupId>jakarta.data</groupId>
                      <artifactId>jakarta.data-api</artifactId>
                      <version>1.0.1</version>
                    </dependency>
                    <dependency>
                      <groupId>jakarta.data</groupId>
                      <artifactId>jakarta.data-spec</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <!--~~(Module has dependency: jakarta.data:*:%s)~~>--><project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>

                  <dependencies>
                    <dependency>
                      <groupId>jakarta.data</groupId>
                      <artifactId>jakarta.data-api</artifactId>
                      <version>1.0.1</version>
                    </dependency>
                    <dependency>
                      <groupId>jakarta.data</groupId>
                      <artifactId>jakarta.data-spec</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(versionPattern)
            )
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "1.0.1", // exact
      "1.0.1-1.0.5", // hyphenated
      "[1.0.1,1.0.5)", "[1.0.1,1.0.5]", "[1.0.1,1.0.5]", "(1.0.0,1.0.5]", // full range
      "~1.0.1"// tilde range
    })
    void gradle(String versionPattern) {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new ModuleHasDependency("jakarta.data", "*", null, versionPattern, null)),
          mavenProject("project-maven",
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
                    implementation 'jakarta.data:jakarta.data-api:1.0.1'
                    implementation 'jakarta.data:jakarta.data-spec:1.0.0'
                }
                """,
              """
                /*~~(Module has dependency: jakarta.data:*:%s)~~>*/plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'jakarta.data:jakarta.data-api:1.0.1'
                    implementation 'jakarta.data:jakarta.data-spec:1.0.0'
                }
                """.formatted(versionPattern)
            )
          )
        );
    }

    @Test
    void noPresentVersion() {
        rewriteRun(
          recipeSpec -> recipeSpec.recipe(new ModuleHasDependency("org.springframework", "*", null, "5.1.2", null)),
          mavenProject("project-maven",
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
                    implementation 'org.springframework:spring-aop:6.1.5'
                }
                """
            )
          )
        );
    }
}
}
