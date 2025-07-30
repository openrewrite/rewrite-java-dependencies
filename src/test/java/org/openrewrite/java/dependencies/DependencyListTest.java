/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.java.dependencies.table.DependencyListReport;
import org.openrewrite.maven.tree.*;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

@SuppressWarnings("GroovyUnusedAssignment")
class DependencyListTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyList(DependencyList.Scope.Compile, true, false));
    }

    @Test
    void basic() {
        rewriteRun(
          spec -> spec
            .beforeRecipe(withToolingApi())
            .dataTable(DependencyListReport.Row.class, rows -> {
                assertThat(rows)
                  .filteredOn(it -> "Maven".equals(it.getBuildTool()) && "rewrite-core".equals(it.getDependencyArtifactId()))
                  .hasSize(1);
                assertThat(rows)
                  .filteredOn(it -> "Gradle".equals(it.getBuildTool()) && "rewrite-core".equals(it.getDependencyArtifactId()))
                  .hasSize(1);
            }),
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
                  implementation('org.openrewrite:rewrite-core:7.39.0')
              }
              """
          ),
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
                      <version>7.39.0</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void directOnly() {
        rewriteRun(
          spec -> spec.recipe(new DependencyList(DependencyList.Scope.Compile, false, false))
            .beforeRecipe(withToolingApi())
            .dataTable(DependencyListReport.Row.class, rows -> {
                assertThat(rows)
                  .containsExactlyInAnyOrder(
                    new DependencyListReport.Row("Gradle", "com.test", "test", "1.0.0", "io.micrometer.prometheus", "prometheus-rsocket-client", "1.5.3", true, ""),
                    new DependencyListReport.Row("Maven", "com.test", "test", "1.0.0", "io.micrometer.prometheus", "prometheus-rsocket-client", "1.5.3", true, ""));
            }),
          settingsGradle("rootProject.name = 'test'"),
          buildGradle(
            //language=groovy
            """
              plugins {
                  id 'java'
              }
              group = 'com.test'
              version = '1.0.0'
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation('io.micrometer.prometheus:prometheus-rsocket-client:1.5.3')
              }
              """
          ),
          pomXml(
            //language=xml
            """
              <project>
                  <groupId>com.test</groupId>
                  <artifactId>test</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.micrometer.prometheus</groupId>
                          <artifactId>prometheus-rsocket-client</artifactId>
                          <version>1.5.3</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void validateResolvable() {
        rewriteRun(
          spec -> spec.recipe(new DependencyList(DependencyList.Scope.Compile, false, true))
            .dataTable(DependencyListReport.Row.class, rows -> assertThat(rows)
              .singleElement()
              .extracting(DependencyListReport.Row::getResolutionFailure)
              .matches(it -> it.startsWith("org.openrewrite.maven.MavenDownloadingException"))),
          xml(
            //language=xml
            """
              <project>
                  <groupId>com.test</groupId>
                  <artifactId>test</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.test</groupId>
                          <artifactId>doesnotexist</artifactId>
                          <version>1.0.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> {
                // Manually construct a resolution result since, obviously, the above cannot be resolved
                MavenRepository pretendRepo = MavenRepository.builder()
                  .id("nonexistent")
                  .uri("https://nonexistent")
                  .build();
                Dependency requested = Dependency.builder()
                  .gav(new GroupArtifactVersion("com.test", "doesnotexist", "1.0.0"))
                  .build();
                ResolvedGroupArtifactVersion rgav = new ResolvedGroupArtifactVersion(pretendRepo.getId(), "com.test", "doesnotexist", "1.0.0", null);
                spec.path(Path.of("pom.xml"))
                  .markers(new MavenResolutionResult(
                    randomId(),
                    null,
                    ResolvedPom.builder()
                      .requested(Pom.builder()
                        .gav(rgav)
                        .build())
                      .repositories(singletonList(pretendRepo))
                      .build(),
                    emptyList(),
                    null,
                    singletonMap(Scope.Compile, singletonList(new ResolvedDependency(
                      pretendRepo,
                      rgav, requested, emptyList(), emptyList(), null, null, null, 0, null)
                    )),
                    null,
                    emptyList(),
                    emptyMap()
                  ));
            }
          )
        );
    }
}
