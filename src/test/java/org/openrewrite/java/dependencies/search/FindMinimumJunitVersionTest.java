/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class FindMinimumJUnitVersionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMinimumJUnitVersion(null));
    }

    @Nested
    class Maven {
        @Test
        void minimumJUnit4() {
            rewriteRun(
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """,
                  """
                    <!--~~(junit:junit:4.12)~~>--><project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void minimumJUnit5() {
            rewriteRun(
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """,
                  """
                    <!--~~(org.junit.jupiter:junit-jupiter-api:5.8.1)~~>--><project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void minimumJUnit4AndJUnit5Present() {
            rewriteRun(
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """,
                  """
                    <!--~~(junit:junit:4.12)~~>--><project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void minimumJUnitNotPresent() {
            rewriteRun(
              mavenProject(
                "server",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>server</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-core</artifactId>
                              <version>2.15.0</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit4IsMinimum() {
            rewriteRun(
              spec -> spec.recipe(new FindMinimumJUnitVersion("4")),
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """,
                  """
                    <!--~~(junit:junit:4.12)~~>--><project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit5IsMinimum() {
            rewriteRun(
              spec -> spec.recipe(new FindMinimumJUnitVersion("5")),
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """,
                  """
                    <!--~~(org.junit.jupiter:junit-jupiter-api:5.8.1)~~>--><project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit5IsMinimumButJUnit4IsUsed() {
            rewriteRun(
              spec -> spec.recipe(new FindMinimumJUnitVersion("5")),
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                          <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit</artifactId>
                              <version>4.12</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit4IsMinimumButJUnit5IsUsed() {
            rewriteRun(
              spec -> spec.recipe(new FindMinimumJUnitVersion("4")),
              mavenProject(
                "core",
                //language=xml
                pomXml(
                  """
                    <project>
                      <groupId>org.openrewrite</groupId>
                      <artifactId>core</artifactId>
                      <version>0.1.0-SNAPSHOT</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter-api</artifactId>
                              <version>5.8.1</version>
                          </dependency>
                      </dependencies>
                    </project>
                    """
                )
              )
            );
        }
    }

    @Nested
    class Gradle {
        @Test
        void minimumJUnit4() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'junit:junit:4.12'
                    }
                    """,
                  """
                    /*~~(junit:junit:4.12)~~>*/plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'junit:junit:4.12'
                    }
                    """
                )
              )
            );
        }

        @Test
        void minimumJUnit5() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                    }
                    """,
                  """
                    /*~~(org.junit.jupiter:junit-jupiter-api:5.8.1)~~>*/plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                    }
                    """
                )
              )
            );
        }

        @Test
        void minimumJUnit4AndJUnit5Present() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'junit:junit:4.12'
                    }
                    """,
                  """
                    /*~~(junit:junit:4.12)~~>*/plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'junit:junit:4.12'
                    }
                    """
                )
              )
            );
        }

        @Test
        void minimumJUnitNotPresent() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.0'
                    }
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit4IsMinimum() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()).recipe(new FindMinimumJUnitVersion("4")),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'junit:junit:4.12'
                        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                    }
                    """,
                  """
                    /*~~(junit:junit:4.12)~~>*/plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'junit:junit:4.12'
                        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                    }
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit5IsMinimumButJUnit4IsUsed() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()).recipe(new FindMinimumJUnitVersion("5")),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                        testImplementation 'junit:junit:4.12'
                    }
                    """
                )
              )
            );
        }

        @Test
        void findIfJUnit4IsMinimumButJUnit5IsUsed() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()).recipe(new FindMinimumJUnitVersion("4")),
              mavenProject(
                "core",
                //language=groovy
                buildGradle(
                  """
                    plugins { id 'java' }
                    repositories { mavenCentral() }
                    dependencies {
                        testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                    }
                    """
                )
              )
            );
        }
    }
}
