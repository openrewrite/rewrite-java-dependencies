/*
 * Copyright 2026 the original author or authors.
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
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class RepositoryHasDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi());
    }

    @DocumentExample
    @Test
    void usedAsDeclarativePrecondition() {
        rewriteRun(
          //language=yaml
          spec -> spec.recipeFromYaml("""
            type: specs.openrewrite.org/v1beta/recipe
            name: org.openrewrite.test.UsePrecondition
            description: Test recipe using RepositoryHasDependency as a precondition.
            preconditions:
              - org.openrewrite.java.dependencies.search.RepositoryHasDependency:
                  groupIdPattern: org.springframework
                  artifactIdPattern: spring-beans
            recipeList:
              - org.openrewrite.java.dependencies.search.FindMinimumDependencyVersion:
                  groupIdPattern: org.springframework
                  artifactIdPattern: spring-beans
            """, "org.openrewrite.test.UsePrecondition"),
          mavenProject("project",
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
              """
                <!--~~(org.springframework:spring-beans:6.0.0)~~>--><project>
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
            )
          )
        );
    }

    @Language("java")
    private final static String GradleJava = """
      public class AGradle {}
      """;

    @Test
    void gradleVersionRangeDoesNotMatchDeclaredWhenResolvedVersionIsOutOfRange() {
        // Declared spring-beans 5.3.0 (in range), but resolutionStrategy forces resolved 6.0.0
        // (out of range). The resolved version is the source of truth, so the declared-dependency
        // fallback must be skipped for an already-resolved coordinate and [5.0,6.0) must NOT match.
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, "[5.0,6.0)")),
          mavenProject("project-gradle",
            //language=groovy
            buildGradle("""
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
              """),
            java(GradleJava)
          )
        );
    }

    @Test
    void gradleRequestedWithoutVersionAndConstraintDoesNotMatch() {
        // Resolution fails (no repositories), so the requested fallback fires. The declared
        // dependency omits a version (supplied elsewhere by a platform/constraint), so its
        // requested version is null and must NOT match the supplied version constraint (same
        // treatment as a ${...} property reference).
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, "[1.0,)")),
          mavenProject("project-gradle",
            //language=groovy
            buildGradle("""
              plugins {
                id 'java-library'
              }
              dependencies {
                implementation 'org.springframework:spring-beans'
              }
              """),
            java(GradleJava)
          )
        );
    }
}
