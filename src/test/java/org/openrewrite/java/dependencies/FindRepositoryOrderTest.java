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
package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.maven.table.MavenRepositoryOrder;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class FindRepositoryOrderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindRepositoryOrder());
    }

    @DocumentExample
    @Test
    void findMavenRepositoryOrder() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <repositories>
                  <repository>
                    <id>myRepo</id>
                    <url>https://myrepo.maven.com/repo</url>
                  </repository>
                </repositories>
              </project>
              """,
            """
              <!--~~(https://myrepo.maven.com/repo)~~>--><project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <repositories>
                  <repository>
                    <id>myRepo</id>
                    <url>https://myrepo.maven.com/repo</url>
                  </repository>
                </repositories>
              </project>
              """
          )
        );
    }

    @Test
    void findGradleRepositoryOrder() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              repositories {
                  maven { url 'https://repo.spring.io/milestone' }
              }
              """,
            """
              /*~~(https://repo.spring.io/milestone)~~>*/plugins {
                  id 'java'
              }

              repositories {
                  maven { url 'https://repo.spring.io/milestone' }
              }
              """
          )
        );
    }

    @Test
    void producesDataTableForBothBuildSystems() {
        rewriteRun(
          spec -> spec
            .beforeRecipe(withToolingApi())
            .dataTable(MavenRepositoryOrder.Row.class, rows ->
              assertThat(rows).anySatisfy(row ->
                assertThat(row.getUri()).isEqualTo("https://myrepo.maven.com/repo")
              ).anySatisfy(row ->
                assertThat(row.getUri()).isEqualTo("https://repo.spring.io/milestone")
              )),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <repositories>
                  <repository>
                    <id>myRepo</id>
                    <url>https://myrepo.maven.com/repo</url>
                  </repository>
                </repositories>
              </project>
              """,
            """
              <!--~~(https://myrepo.maven.com/repo)~~>--><project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <repositories>
                  <repository>
                    <id>myRepo</id>
                    <url>https://myrepo.maven.com/repo</url>
                  </repository>
                </repositories>
              </project>
              """
          ),
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              repositories {
                  maven { url 'https://repo.spring.io/milestone' }
              }
              """,
            """
              /*~~(https://repo.spring.io/milestone)~~>*/plugins {
                  id 'java'
              }

              repositories {
                  maven { url 'https://repo.spring.io/milestone' }
              }
              """
          )
        );
    }
}
