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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

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
    class WhenDependencyIsRequestedButNotResolved {

        @Language("groovy")
        private final static String GradleNoRepositories = """
          plugins {
            id 'java-library'
          }
          dependencies {
            implementation 'org.springframework:spring-beans:6.0.0'
          }
          """;

        @Language("xml")
        private final static String MavenNoRepositories = """
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

        @Test
        void gradleMatchesOnRequested() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, null)),
              mavenProject("project-gradle",
                buildGradle(
                  GradleNoRepositories,
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
              )
            );
        }

        @Test
        void mavenMatchesOnRequested() {
            rewriteRun(
              spec -> {
                  MavenExecutionContextView ctx = MavenExecutionContextView.view(new InMemoryExecutionContext());
                  MavenSettings emptySettings = MavenSettings.parse(new Parser.Input(Path.of("settings.xml"), () -> new ByteArrayInputStream(
                    //language=xml
                    """
                      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd"/>
                      """.getBytes())), ctx);
                  ctx.setMavenSettings(emptySettings);
                  spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, null, null))
                    .executionContext(ctx);
              },
              mavenProject("project-maven",
                pomXml(
                  MavenNoRepositories,
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
        void gradleVersionRangeOnRequestedDoesNotMatchWhenOutOfRange() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, "[7.0,)", null)),
              mavenProject("project-gradle",
                buildGradle(GradleNoRepositories),
                java(GradleJava)
              )
            );
        }

        @Language("groovy")
        private final static String GradleNoRepositoriesNoVersion = """
          plugins {
            id 'java-library'
          }
          dependencies {
            implementation 'org.springframework:spring-beans'
          }
          """;

        @Test
        void gradleRequestedWithoutVersionAndConstraintDoesNotMatch() {
            // Force resolution failure (no repositories), so the requested fallback fires.
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, "[1.0,)", null)),
              mavenProject("project-gradle",
                buildGradle(GradleNoRepositoriesNoVersion),
                java(GradleJava)
              )
            );
        }
    }

    @Nested
    class WhenResolvedVersionIsSourceOfTruth {

        @Language("groovy")
        private final static String GradleForcedOutOfRange = """
          plugins {
            id 'java-library'
          }
          repositories {
            mavenCentral()
          }
          configurations.all {
            resolutionStrategy {
              force 'org.springframework:spring-beans:6.0.0'
            }
          }
          dependencies {
            implementation 'org.springframework:spring-beans:5.3.0'
          }
          """;

        @Test
        void gradleVersionRangeDoesNotMatchDeclaredWhenResolvedVersionIsOutOfRange() {
            // The declared-dependency fallback must be skipped for an already-resolved coordinate (resolutionStrategy).
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GroupId, ArtifactId, null, "[5.0,6.0)", null)),
              mavenProject("project-gradle",
                buildGradle(GradleForcedOutOfRange),
                java(GradleJava)
              )
            );
        }
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
    void invertedWithVersionRangeMarksModulesWithoutOldKotlin() {
        var groupId = "org.jetbrains.kotlin";
        var artifactId = "kotlin-stdlib";
        var versionRange = "[0,2.3)";
        var negativeSub = "(Module does not have dependency: %s:%s:%s)~~".formatted(groupId, artifactId, versionRange);
        var mavenMarker = "<!--~~%s>-->".formatted(negativeSub);
        var javaMarker = "/*~~%s>*/".formatted(negativeSub);
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(groupId, artifactId, null, versionRange, true)),
          // Module with old Kotlin (2.1.0) — should NOT be marked
          mavenProject("old-kotlin",
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>old-kotlin</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.jetbrains.kotlin</groupId>
                      <artifactId>kotlin-stdlib</artifactId>
                      <version>2.1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            java("public class OldKotlin {}")
          ),
          // Module with new Kotlin (2.3.0) — SHOULD be marked
          mavenProject("new-kotlin",
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>new-kotlin</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.jetbrains.kotlin</groupId>
                      <artifactId>kotlin-stdlib</artifactId>
                      <version>2.3.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(mavenMarker)
                  .actual()
              )
            ),
            java(
              "public class NewKotlin {}",
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(javaMarker)
                  .actual()
              )
            )
          ),
          // Module with no Kotlin — SHOULD be marked
          mavenProject("no-kotlin",
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>no-kotlin</artifactId>
                  <version>1.0.0</version>
                </project>
                """,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(mavenMarker)
                  .actual()
              )
            ),
            java(
              "public class NoKotlin {}",
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(javaMarker)
                  .actual()
              )
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
