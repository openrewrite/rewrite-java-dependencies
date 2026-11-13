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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void usedAsInvertedDeclarativePrecondition() {
        //language=yaml
        Consumer<RecipeSpec> recipe = spec -> spec.recipeFromYaml("""
          type: specs.openrewrite.org/v1beta/recipe
          name: org.openrewrite.test.UseInvertedPrecondition
          description: Test recipe using RepositoryHasDependency with invertMarking as a precondition.
          preconditions:
            - org.openrewrite.java.dependencies.search.RepositoryHasDependency:
                groupIdPattern: org.springframework
                artifactIdPattern: spring-beans
                invertMarking: true
          recipeList:
            - org.openrewrite.FindSourceFiles:
                filePattern: "**/pom.xml"
          """, "org.openrewrite.test.UseInvertedPrecondition");

        rewriteRun(
          recipe,
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                </project>
                """,
              """
                <!--~~>--><project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            )
          )
        );

        rewriteRun(
          recipe,
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
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
          ),
          mavenProject("lib",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            )
          )
        );
    }

    @Test
    void invertedMarkingMarksEverySourceWhenNoModuleHasDependency() {
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, null, true)),
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                </project>
                """,
              """
                <!--~~(Repository does not have dependency: org.springframework:spring-beans)~~>--><project>
                  <groupId>com.example</groupId>
                  <artifactId>foo</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            ),
            //language=java
            java(
              "class A {}",
              "/*~~(Repository does not have dependency: org.springframework:spring-beans)~~>*/class A {}"
            )
          )
        );
    }

    @Test
    void invertedMarkingMarksNothingWhenAnyModuleHasDependency() {
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, null, true)),
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
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
          ),
          mavenProject("lib",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                </project>
                """
            ),
            //language=java
            java("class A {}")
          )
        );
    }

    @Language("java")
    private final static String GradleJava = """
      public class AGradle {}
      """;

    @Test
    void gradleVersionRangeDoesNotMatchDeclaredWhenResolvedVersionIsOutOfRange() {
        // The declared-dependency fallback must be skipped for an already-resolved coordinate (resolutionStrategy).
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, "[5.0,6.0)", null)),
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
    void mavenVersionRangeDoesNotMatchBomManagedDependencyWhenResolvedIsOutOfRange() {
        // Regression for rewrite-third-party#76: BOM-managed version (null on requested) must not match the range.
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, "[5.0,6.0)", null)),
          mavenProject("project-maven",
            //language=xml
            pomXml("""
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
              """)
          )
        );
    }

    @Test
    void gradleRequestedWithoutVersionAndConstraintDoesNotMatch() {
        // Force resolution failure (no repositories), so the requested fallback fires.
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.springframework", "spring-beans", null, "[1.0,)", null)),
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

    @ParameterizedTest
    @ValueSource(strings = {"14.0.14-LTS", "15.0.0-RC1"})
    void versionRangeMatchesAQualifiedVersion(String version) {
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.primefaces", "primefaces", null, "[14,16)", null)),
          mavenProject("project-maven",
            pomXml(primeFacesPom(version), "<!--~~(Repository has dependency: org.primefaces:primefaces:[14,16))~~>-->" + primeFacesPom(version))
          )
        );
    }

    @Test
    void versionRangeDoesNotMatchAPreReleaseOfItsUpperBound() {
        rewriteRun(
          spec -> spec.recipe(new RepositoryHasDependency("org.primefaces", "primefaces", null, "[14,16)", null)),
          mavenProject("project-maven",
            pomXml(primeFacesPom("16.0.0-RC1"))
          )
        );
    }

    private static String primeFacesPom(String version) {
        //language=xml
        return """
          <project>
            <groupId>com.example</groupId>
            <artifactId>foo</artifactId>
            <version>1.0.0</version>
            <dependencies>
              <dependency>
                <groupId>org.primefaces</groupId>
                <artifactId>primefaces</artifactId>
                <version>%s</version>
              </dependency>
            </dependencies>
          </project>
          """.formatted(version);
    }

    @Test
    void invalidOptionsAreInvalid() {
        assertThat(new RepositoryHasDependency("junit", "junit", null, "latest.bogus", null).validate().isValid()).isFalse();
        assertThat(new RepositoryHasDependency("junit", "junit", "bogus", null, null).validate().isValid()).isFalse();
    }
}
