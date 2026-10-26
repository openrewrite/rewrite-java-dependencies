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
package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.maven.table.MavenMetadataFailures;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeTransitiveDependencyVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeTransitiveDependencyVersion(
          "com.fasterxml.jackson.core", "jackson-core", "2.12.5", null, null, null, null, null, null, null, null));
    }

    @DocumentExample
    @Test
    void maven() {
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
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.12.0</version>
                      </dependency>
                  </dependencies>
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
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-core</artifactId>
                              <version>2.12.5</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.12.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation 'com.fasterxml.jackson.core:jackson-databind:2.12.0'
              }
              """,
            """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  constraints {
                      implementation('com.fasterxml.jackson.core:jackson-core:2.12.5')
                  }

                  implementation 'com.fasterxml.jackson.core:jackson-databind:2.12.0'
              }
              """
          )
        );
    }

    @Test
    void leavesDirectDependencyUnchanged() {
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
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>2.12.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    private static UpgradeTransitiveDependencyVersion upgradeLog4jBom() {
        return new UpgradeTransitiveDependencyVersion("org.apache.logging.log4j", "log4j-bom", "2.24.x",
          null, null, null, null, null, null, null, null);
    }

    @Test
    void declaresMavenMetadataFailuresDataTable() {
        assertThat(upgradeLog4jBom().getDataTableDescriptors())
          .extracting(DataTableDescriptor::getName)
          .contains(MavenMetadataFailures.class.getName());
    }

    @Test
    void instanceNameShowsTheDependencyAndTargetVersion() {
        var recipe = upgradeLog4jBom();
        assertThat(recipe.getInstanceName()).isEqualTo(recipe.getDisplayName() + " `org.apache.logging.log4j:log4j-bom:2.24.x`");
    }

    @Test
    void instanceNameOmitsTheSuffixWhenUnconfigured() {
        var recipe = new UpgradeTransitiveDependencyVersion(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(recipe.getInstanceName()).isEqualTo(recipe.getDisplayName());
    }

    @Test
    void invalidVersionIsInvalid() {
        assertThat(new UpgradeTransitiveDependencyVersion("junit", "junit", "latest.bogus", null, null, null, null, null, null, null, null)
          .validate().isValid()).isFalse();
    }

    @Test
    void missingVersionIsInvalid() {
        assertThat(new UpgradeTransitiveDependencyVersion("junit", "junit", null, null, null, null, null, null, null, null, null)
          .validate().isValid()).isFalse();
    }
}
