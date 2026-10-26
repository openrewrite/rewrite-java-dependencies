/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.maven.table.DependenciesDeclared;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

@SuppressWarnings("GroovyAssignabilityCheck")
class FindDependencyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDependency("org.openrewrite", "rewrite-core", "8.0.0", null, null));
    }

    @DocumentExample
    @Test
    void findMavenDependency() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                    <groupId>org.openrewrite</groupId>
                    <artifactId>rewrite-core</artifactId>
                    <version>8.0.0</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <!--~~>--><dependency>
                    <groupId>org.openrewrite</groupId>
                    <artifactId>rewrite-core</artifactId>
                    <version>8.0.0</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void findGradleDependency() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
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
                  api "org.openrewrite:rewrite-core:8.0.0"
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
                  /*~~>*/api "org.openrewrite:rewrite-core:8.0.0"
              }
              """
          )
        );
    }

    @Test
    void findGradleKotlinDslDependency() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=kotlin
          buildGradleKts(
            """
              plugins {
                  `java-library`
              }

              repositories {
                  maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
              }

              dependencies {
                  api("org.openrewrite:rewrite-core:8.0.0")
              }
              """,
            """
              plugins {
                  `java-library`
              }

              repositories {
                  maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
              }

              dependencies {
                  /*~~>*/api("org.openrewrite:rewrite-core:8.0.0")
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void findMavenDependencyDoesNotFindWrongVersion() {
            rewriteRun(
              //language=xml
              pomXml(
                """
                  <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.openrewrite</groupId>
                        <artifactId>rewrite-core</artifactId>
                        <version>8.1.0</version>
                      </dependency>
                    </dependencies>
                  </project>
                  """
              )
            );
        }

        @Test
        void findGradleDependencyDoesntFindWrongVersion() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
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
                      api "org.openrewrite:rewrite-core:8.1.0"
                  }
                  """
              )
            );
        }
    }

    @Test
    void declaresDependenciesDeclaredDataTable() {
        assertThat(new FindDependency("org.openrewrite", "rewrite-core", null, null, null).getDataTableDescriptors())
          .extracting(DataTableDescriptor::getName)
          .contains(DependenciesDeclared.class.getName());
    }

    @Test
    void instanceNameShowsTheDependencyBeingSearchedFor() {
        var recipe = new FindDependency("org.openrewrite", "rewrite-core", "8.0.0", null, null);
        assertThat(recipe.getInstanceName()).isEqualTo(recipe.getDisplayName() + " `org.openrewrite:rewrite-core:8.0.0`");
    }

    @Test
    void instanceNameOmitsTheVersionWhenNotGiven() {
        var recipe = new FindDependency("org.openrewrite", "rewrite-core", null, null, null);
        assertThat(recipe.getInstanceName()).isEqualTo(recipe.getDisplayName() + " `org.openrewrite:rewrite-core`");
    }

    @Test
    void instanceNameOmitsTheSuffixWhenUnconfigured() {
        var recipe = new FindDependency(null, null, null, null, null);
        assertThat(recipe.getInstanceName()).isEqualTo(recipe.getDisplayName());
    }

    @Test
    void invalidVersionIsInvalid() {
        assertThat(new FindDependency("junit", "junit", "latest.bogus", null, null).validate().isValid()).isFalse();
    }
}
