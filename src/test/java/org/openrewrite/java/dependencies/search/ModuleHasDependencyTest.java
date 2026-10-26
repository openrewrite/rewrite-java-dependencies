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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.dependencies.MavenCentralMirror.settingsMirroringOnlyCentral;
import static org.openrewrite.maven.Assertions.pomXml;

class ModuleHasDependencyTest implements RewriteTest {
    private static final String GROUP_ID = "org.springframework";
    private static final String ARTIFACT_ID = "spring-beans";

    private static final String POSITIVE_SUB = "(Module has dependency: %1$s:%2$s)~~";
    private static final String NEGATIVE_SUB = "(Module does not have dependency: %1$s:%2$s)~~";
    private static final String NEGATIVE_VACUOUS_SUB = "(No module, so vacuously does not have dependency: %1$s:%2$s)~~";
    private static final String JAVA_MARKER_BASE = "/*~~%s>*/";
    private static final String JAVA_MARKER_POSITIVE = JAVA_MARKER_BASE.formatted(POSITIVE_SUB.formatted(GROUP_ID, ARTIFACT_ID));
    private static final String JAVA_MARKER_NEGATIVE = JAVA_MARKER_BASE.formatted(NEGATIVE_SUB.formatted(GROUP_ID, ARTIFACT_ID));
    private static final String JAVA_MARKER_NEGATIVE_VACUOUS = JAVA_MARKER_BASE.formatted(NEGATIVE_VACUOUS_SUB.formatted(GROUP_ID, ARTIFACT_ID));
    private static final String GRADLE_MARKER_POSITIVE = JAVA_MARKER_POSITIVE;
    private static final String GRADLE_MARKER_NEGATIVE = JAVA_MARKER_NEGATIVE;
    private static final String MAVEN_MARKER_BASE = "<!--~~%s>-->";
    private static final String MAVEN_MARKER_POSITIVE = MAVEN_MARKER_BASE.formatted(POSITIVE_SUB.formatted(GROUP_ID, ARTIFACT_ID));
    private static final String MAVEN_MARKER_NEGATIVE = MAVEN_MARKER_BASE.formatted(NEGATIVE_SUB.formatted(GROUP_ID, ARTIFACT_ID));

    @Language("groovy")
    private static final String GRADLE_NONE = """
      plugins {
        id 'java-library'
      }
      repositories {
        mavenCentral()
      }
      """;

    @Language("xml")
    private static final String MAVEN_NONE = """
      <project>
        <groupId>com.example</groupId>
        <artifactId>foo</artifactId>
        <version>1.0.0</version>
      </project>
      """;

    @Language("groovy")
    private static final String GRADLE_DIRECT = """
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
    private static final String MAVEN_DIRECT = """
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
    private static final String GRADLE_TRANSITIVE = """
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
    private static final String MAVEN_TRANSITIVE = """
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
    private static final String GRADLE_JAVA = """
      public class AGradle {}
      """;

    @Language("java")
    private static final String MAVEN_JAVA = """
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
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, invertCondition)),
          java(GRADLE_JAVA)
        );
    }

    @Test
    void whenNoModuleButInvertedMarkingMarks() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, true)),
          java(
            GRADLE_JAVA,
            spec -> spec.after(actual ->
              assertThat(actual)
                .startsWith(JAVA_MARKER_NEGATIVE_VACUOUS)
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
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, invertCondition)),
          mavenProject("project-gradle",
            buildGradle(
              GRADLE_DIRECT,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GRADLE_MARKER_POSITIVE)
                  .actual()
              )
            ),
            java(
              GRADLE_JAVA,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JAVA_MARKER_POSITIVE)
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            pomXml(
              MAVEN_DIRECT,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MAVEN_MARKER_POSITIVE)
                  .actual()
              )
            ),
            java(
              MAVEN_JAVA,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JAVA_MARKER_POSITIVE)
                  .actual()
              )
            )
          )
        );
    }

    @Test
    void whenModuleHasDirectDependencyButInvertedMarkingDoesNotMark() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, true)),
          mavenProject("project",
            buildGradle(GRADLE_DIRECT),
            java(GRADLE_JAVA)
          ),
          mavenProject("project-maven",
            pomXml(MAVEN_DIRECT),
            java(MAVEN_JAVA)
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleHasTransitiveDependencyMarks(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, invertCondition)),
          mavenProject("project-gradle",
            buildGradle(
              GRADLE_TRANSITIVE,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GRADLE_MARKER_POSITIVE)
                  .actual()
              )
            ),
            java(
              GRADLE_JAVA,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JAVA_MARKER_POSITIVE)
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            pomXml(
              MAVEN_TRANSITIVE,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MAVEN_MARKER_POSITIVE)
                  .actual()
              )
            ),
            java(
              MAVEN_JAVA,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JAVA_MARKER_POSITIVE)
                  .actual()
              )
            )
          )
        );
    }

    @Test
    void whenModuleHasTransitiveDependencyButInvertedMarkingDoesNotMark() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, true)),
          mavenProject("project",
            buildGradle(GRADLE_TRANSITIVE),
            java(GRADLE_JAVA)
          ),
          mavenProject("project-maven",
            pomXml(MAVEN_TRANSITIVE),
            java(MAVEN_JAVA)
          )
        );
    }

    @NullSource
    @ParameterizedTest
    @ValueSource(booleans = {false})
    void whenModuleDoesNotHaveDependencyDoesNotMark(Boolean invertCondition) {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, invertCondition)),
          mavenProject("project-gradle",
            buildGradle(GRADLE_NONE),
            java(GRADLE_JAVA)
          ),
          mavenProject("project-maven",
            pomXml(MAVEN_NONE),
            java(MAVEN_JAVA)
          )
        );
    }

    @Test
    void whenModuleDoesNotHaveDependencyButInvertedMarkingMarks() {
        rewriteRun(
          spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, true)),
          mavenProject("project",
            buildGradle(
              GRADLE_NONE,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(GRADLE_MARKER_NEGATIVE)
                  .actual()
              )
            ),
            java(
              GRADLE_JAVA,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JAVA_MARKER_NEGATIVE)
                  .actual()
              )
            )
          ),
          mavenProject("project-maven",
            pomXml(
              MAVEN_NONE,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(MAVEN_MARKER_NEGATIVE)
                  .actual()
              )
            ),
            java(
              MAVEN_JAVA,
              spec -> spec.after(actual ->
                assertThat(actual)
                  .startsWith(JAVA_MARKER_NEGATIVE)
                  .actual()
              )
            )
          )
        );
    }

    @Nested
    class WhenDependencyIsRequestedButNotResolved {

        @Language("groovy")
        private static final String GRADLE_NO_REPOSITORIES = """
          plugins {
            id 'java-library'
          }
          dependencies {
            implementation 'org.springframework:spring-beans:6.0.0'
          }
          """;

        @Language("xml")
        private static final String MAVEN_NO_REPOSITORIES = """
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
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, null)),
              mavenProject("project-gradle",
                buildGradle(
                  GRADLE_NO_REPOSITORIES,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(GRADLE_MARKER_POSITIVE)
                      .actual()
                  )
                ),
                java(
                  GRADLE_JAVA,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(JAVA_MARKER_POSITIVE)
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
                  ctx.setMavenSettings(settingsMirroringOnlyCentral(ctx));
                  spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, null, null))
                    .executionContext(ctx);
              },
              mavenProject("project-maven",
                pomXml(
                  MAVEN_NO_REPOSITORIES,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(MAVEN_MARKER_POSITIVE)
                      .actual()
                  )
                ),
                java(
                  MAVEN_JAVA,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(JAVA_MARKER_POSITIVE)
                      .actual()
                  )
                )
              )
            );
        }

        @Test
        void gradleVersionRangeOnRequestedDoesNotMatchWhenOutOfRange() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, "[7.0,)", null, null)),
              mavenProject("project-gradle",
                buildGradle(GRADLE_NO_REPOSITORIES),
                java(GRADLE_JAVA)
              )
            );
        }

        @Language("groovy")
        private static final String GRADLE_NO_REPOSITORIES_NO_VERSION = """
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
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, "[1.0,)", null, null)),
              mavenProject("project-gradle",
                buildGradle(GRADLE_NO_REPOSITORIES_NO_VERSION),
                java(GRADLE_JAVA)
              )
            );
        }
    }

    @Nested
    class WhenResolvedVersionIsSourceOfTruth {

        @Language("groovy")
        private static final String GRADLE_FORCED_OUT_OF_RANGE = """
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
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, "[5.0,6.0)", null, null)),
              mavenProject("project-gradle",
                buildGradle(GRADLE_FORCED_OUT_OF_RANGE),
                java(GRADLE_JAVA)
              )
            );
        }

        @Language("xml")
        private static final String MAVEN_BOM_MANAGED_OUT_OF_RANGE = """
          <project>
            <groupId>com.example</groupId>
            <artifactId>foo</artifactId>
            <version>1.0.0</version>
            <dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-dependencies</artifactId>
                  <version>3.0.0</version>
                  <type>pom</type>
                  <scope>import</scope>
                </dependency>
              </dependencies>
            </dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>org.springframework</groupId>
                <artifactId>spring-beans</artifactId>
              </dependency>
            </dependencies>
          </project>
          """;

        @Test
        void mavenVersionRangeDoesNotMatchBomManagedDependencyWhenResolvedIsOutOfRange() {
            // Regression for rewrite-third-party#76: BOM-managed version (null on requested) must not match the range.
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, "[5.0,6.0)", null, null)),
              mavenProject("project-maven",
                pomXml(MAVEN_BOM_MANAGED_OUT_OF_RANGE),
                java(MAVEN_JAVA)
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
          "[1.0.1,1.0.5)", "[1.0.1,1.0.5]", "(1.0.0,1.0.5]", // full range
          "~1.0.1"// tilde range
        })
        void maven(String versionPattern) {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency("jakarta.data", "*", null, versionPattern, null, null)),
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
          "[1.0.1,1.0.5)", "[1.0.1,1.0.5]", "(1.0.0,1.0.5]", // full range
          "~1.0.1"// tilde range
        })
        void gradle(String versionPattern) {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency("jakarta.data", "*", null, versionPattern, null, null)),
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
              spec -> spec.recipe(new ModuleHasDependency(groupId, artifactId, null, versionRange, null, true)),
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
    }

    @Nested
    class OnlyDirect {

        @Test
        void skipsMavenTransitiveMatch() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, true, null)),
              mavenProject("project-maven",
                pomXml(MAVEN_TRANSITIVE),
                java(MAVEN_JAVA)
              )
            );
        }

        @DocumentExample
        @Test
        void stillMarksMavenDirectMatch() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, true, null)),
              mavenProject("project-maven",
                pomXml(
                  MAVEN_DIRECT,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(MAVEN_MARKER_POSITIVE)
                      .actual()
                  )
                ),
                java(
                  MAVEN_JAVA,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(JAVA_MARKER_POSITIVE)
                      .actual()
                  )
                )
              )
            );
        }

        @Test
        void skipsGradleTransitiveMatch() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, true, null)),
              mavenProject("project-gradle",
                buildGradle(GRADLE_TRANSITIVE),
                java(GRADLE_JAVA)
              )
            );
        }

        @Test
        void stillMarksGradleDirectMatch() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, true, null)),
              mavenProject("project-gradle",
                buildGradle(
                  GRADLE_DIRECT,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(GRADLE_MARKER_POSITIVE)
                      .actual()
                  )
                ),
                java(
                  GRADLE_JAVA,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(JAVA_MARKER_POSITIVE)
                      .actual()
                  )
                )
              )
            );
        }

        @Test
        void withInvertMarkingMarksTransitiveOnlyModules() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency(GROUP_ID, ARTIFACT_ID, null, null, true, true)),
              mavenProject("project-gradle",
                buildGradle(
                  GRADLE_TRANSITIVE,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(GRADLE_MARKER_NEGATIVE)
                      .actual()
                  )
                ),
                java(
                  GRADLE_JAVA,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(JAVA_MARKER_NEGATIVE)
                      .actual()
                  )
                )
              ),
              mavenProject("project-maven",
                pomXml(
                  MAVEN_TRANSITIVE,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(MAVEN_MARKER_NEGATIVE)
                      .actual()
                  )
                ),
                java(
                  MAVEN_JAVA,
                  spec -> spec.after(actual ->
                    assertThat(actual)
                      .startsWith(JAVA_MARKER_NEGATIVE)
                      .actual()
                  )
                )
              )
            );
        }
    }

    @Test
    void invalidOptionsAreInvalid() {
        assertThat(new ModuleHasDependency("junit", "junit", null, "latest.bogus", null, null).validate().isValid()).isFalse();
        assertThat(new ModuleHasDependency("junit", "junit", "bogus", null, null, null).validate().isValid()).isFalse();
    }

    @Nested
    class NoChange {
        @Test
        void noPresentVersion() {
            rewriteRun(
              spec -> spec.recipe(new ModuleHasDependency("org.springframework", "*", null, "5.1.2", null, null)),
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
