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


import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.test.RewriteTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeDependencyVersionTest implements RewriteTest {

    @DocumentExample("Upgrade gradle dependency")
    @Test
    void upgradeGuavaInGradleProject() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new UpgradeDependencyVersion("com.google.guava", "guava", "30.x", "-jre", null, null)),
          buildGradle(
            //language=groovy
            """
              plugins {
                id 'java-library'
              }
                            
              repositories {
                mavenCentral()
              }
                            
              dependencies {
                compileOnly 'com.google.guava:guava:29.0-jre'
                runtimeOnly ('com.google.guava:guava:29.0-jre')
              }
              """,
            //language=groovy
            """
              plugins {
                id 'java-library'
              }
                            
              repositories {
                mavenCentral()
              }
                            
              dependencies {
                compileOnly 'com.google.guava:guava:30.1.1-jre'
                runtimeOnly ('com.google.guava:guava:30.1.1-jre')
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

    @Test
    void updateManagedDependencyVersion() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion("org.junit.jupiter", "junit-jupiter-api", "5.7.2", null,
            null, null)),
          //language=xml
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
}
