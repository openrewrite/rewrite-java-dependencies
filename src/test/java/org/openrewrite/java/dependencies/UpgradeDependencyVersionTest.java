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


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.test.RewriteTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

public class UpgradeDependencyVersionTest implements RewriteTest {

    @DocumentExample("Upgrade gradle dependency")
    @Test
    void upgradeGuavaInGradleProject() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new UpgradeDependencyVersion("com.google.guava", "guava", "30.x", "-jre", null, null)),
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                compileOnly 'com.google.guava:guava:29.0-jre'
                runtimeOnly ('com.google.guava:guava:29.0-jre') {
                    force = true
                }
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
                compileOnly 'com.google.guava:guava:30.1.1-jre'
                runtimeOnly ('com.google.guava:guava:30.1.1-jre') {
                    force = true
                }
              }
              """,
            spec -> spec.afterRecipe(after -> {
                Optional<GradleProject> maybeGp = after.getMarkers().findFirst(GradleProject.class);
                assertThat(maybeGp).isPresent();
                GradleProject gp = maybeGp.get();
                GradleDependencyConfiguration compileClasspath = gp.getConfiguration("compileClasspath");
                assertThat(compileClasspath).isNotNull();
                assertThat(
                  compileClasspath.getRequested().stream()
                    .filter(dep -> "com.google.guava".equals(dep.getGroupId()) && "guava".equals(dep.getArtifactId()) && "30.1.1-jre".equals(dep.getVersion()))
                    .findAny())
                  .as("GradleProject requested dependencies should have been updated with the new version of guava")
                  .isPresent();
                assertThat(
                  compileClasspath.getResolved().stream()
                    .filter(dep -> "com.google.guava".equals(dep.getGroupId()) && "guava".equals(dep.getArtifactId()) && "30.1.1-jre".equals(dep.getVersion()))
                    .findAny())
                  .as("GradleProject requested dependencies should have been updated with the new version of guava")
                  .isPresent();
            })
          )
        );
    }

    @DocumentExample("Upgrade maven dependency version")
    @Test
    void updateManagedDependencyVersion() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion("org.junit.jupiter", "junit-jupiter-api", "5.7.2", null,
            null, null)),
          pomXml(
            """
              <project>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.6.2</version>
                              <scope>test</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            """
              <project>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.7.2</version>
                              <scope>test</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }

    /**
     * This test tries to emulate how recipes are instantiated and later on their parameters populated via reflection with
     * jackson. This causes that some care has to be taken in this recipe when instantiating the child recipes, since the
     * constructor parameters and fields of the class might not be correct (or final) when some methods are called during
     * instantiation or validation.
     */
    @Test
    void testRecipeInstantiation() throws JsonMappingException {
        // We instantiate the recipe with default values.
        UpgradeDependencyVersion recipe = new UpgradeDependencyVersion("", "", "", null, null, null);

        // We get the RecipeDescriptor (internally calls getRecipeList).
        RecipeDescriptor recipeDescriptor = recipe.getDescriptor();
        assertThat(recipeDescriptor.getRecipeList().size()).isEqualTo(2);

        // This is a similar ObjectMapper than the one used to set up recipes
        ObjectMapper mapper = JsonMapper.builder()
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
          .build()
          .registerModule(new ParameterNamesModule())
          .registerModule(new JavaTimeModule());

        // New parameters to update the recipe
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("groupId", "org.openrewrite.recipe");
        parameters.put("artifactId", "rewrite-java-dependencies");
        parameters.put("newVersion", "1.0.4");

        UpgradeDependencyVersion updatedRecipe = mapper.updateValue(recipe, parameters);

        // We check that it's the same instance that is being updated
        assertThat(updatedRecipe).isSameAs(recipe);

        // We then run the recipe with maven and gradle projects.
        rewriteRun(
          spec -> {
              spec.recipe(updatedRecipe);
              spec.beforeRecipe(withToolingApi());
          },
          pomXml(
            """
              <project>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.openrewrite.recipe</groupId>
                          <artifactId>rewrite-java-dependencies</artifactId>
                          <version>1.0.0</version>
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
                      <dependency>
                          <groupId>org.openrewrite.recipe</groupId>
                          <artifactId>rewrite-java-dependencies</artifactId>
                          <version>1.0.4</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          ),
          buildGradle(
            """
              plugins {
                id 'java-library'
              }
              
              repositories {
                mavenCentral()
              }
              
              dependencies {
                compileOnly 'org.openrewrite.recipe:rewrite-java-dependencies:1.0.0'
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
                compileOnly 'org.openrewrite.recipe:rewrite-java-dependencies:1.0.4'
              }
              """
          )
        );
    }
}
