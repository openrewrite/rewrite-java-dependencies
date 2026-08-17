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
import org.openrewrite.Issue;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveRedundantDependenciesTest implements RewriteTest {
    @DocumentExample
    @Test
    void removeRedundantMavenDependency() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void removeRedundantMavenDependencyInTestScope() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.junit.jupiter", "junit-jupiter-engine")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter-engine</artifactId>
                      <version>6.0.1</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter-api</artifactId>
                      <version>6.0.1</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter-engine</artifactId>
                      <version>6.0.1</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void removeMultipleRedundantDependencies() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-annotations</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void doNotRemoveWhenVersionsDiffer() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.16.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void doNoRemoveWhenExcluded() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                      <exclusions>
                        <exclusion>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void doNotRemoveDirectDependencyItself() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noMatchingParentDependency() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.nonexistent", "nonexistent")),
          mavenProject("my-app",
            //language=xml
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-core</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void keepsTomcatEmbedCoreWhenExclusionsDiffer() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-core</artifactId>
                      <exclusions>
                        <exclusion>
                          <groupId>org.apache.tomcat</groupId>
                          <artifactId>tomcat-embed-programmatic</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepsTomcatEmbedCoreWhenDirectHasNoExclusions() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-core</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepsDirectCompileJunitJupiterWhenTransitiveIsTestScoped() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepsRuntimeTomcatEmbedCoreWhenTransitiveIsCompileScoped() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-core</artifactId>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepsDirectCompileTomcatEmbedCoreWhenProviderIsProvidedScoped() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                      <scope>provided</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-core</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepsJakartaClientWhenTransitiveInheritsUnrelatedExclusions() {
        // Known conservative limitation: resolution propagates ancestor exclusions into each transitive's
        // requested dependency, so the transitive jakarta.ws.rs-api carries an inherited jakarta.activation-api
        // exclusion it could never have honoured, while the direct declaration carries none. Removing this
        // would in fact be safe, but telling that apart requires resolving the coordinate's own closure, so
        // the recipe keeps the dependency rather than risk an unsafe removal.
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-jersey</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>jakarta.ws.rs</groupId>
                      <artifactId>jakarta.ws.rs-api</artifactId>
                      <version>3.1.0</version>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepsJerseyClientWhenDirectExclusionsDifferFromTransitive() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-jersey</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.glassfish.jersey.core</groupId>
                      <artifactId>jersey-client</artifactId>
                      <version>3.1.5</version>
                      <exclusions>
                        <exclusion>
                          <groupId>jakarta.inject</groupId>
                          <artifactId>jakarta.inject-api</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void removesTomcatEmbedCoreWhenExclusionsMatchTransitive() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-core</artifactId>
                      <exclusions>
                        <exclusion>
                          <groupId>org.apache.tomcat</groupId>
                          <artifactId>tomcat-annotations-api</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/8336")
    @Test
    void keepsWebsocketWhenDirectHasNoExclusionButTransitiveDoes() {
        // spring-boot-starter-tomcat excludes tomcat-annotations-api from tomcat-embed-websocket, which still
        // brings its own tomcat-embed-core. A direct websocket with no exclusion is therefore NOT equivalent to
        // the transitively-provided one and must be kept, even though websocket's effective-exclusion set is
        // emptied by mediation (annotations-api is pruned at the shared embed-core node first).
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-web")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-websocket</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/8336")
    @Test
    void removesWebsocketWhenDirectRepeatsTheTransitiveExclusion() {
        // The mirror image: a direct websocket that repeats the same tomcat-annotations-api exclusion the
        // starter applies is truly equivalent to the transitively-provided one and should be removed.
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-web")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.tomcat.embed</groupId>
                      <artifactId>tomcat-embed-websocket</artifactId>
                      <exclusions>
                        <exclusion>
                          <groupId>org.apache.tomcat</groupId>
                          <artifactId>tomcat-annotations-api</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void removeRedundantGradleDependency() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new RemoveRedundantDependencies(
              "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
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
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
                    implementation 'com.fasterxml.jackson.core:jackson-core:2.17.0'
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
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
                }
                """
            )
          )
        );
    }

    @Test
    void doNotRemoveGradleDependencyDeclaringVersionConstraint() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new RemoveRedundantDependencies(
              "com.fasterxml.jackson.core", "jackson-databind")),
          mavenProject("my-app",
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
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
                    implementation('com.fasterxml.jackson.core:jackson-core') {
                        version {
                            prefer '2.17.0'
                            strictly '[2.17.0,2.18.0)'
                        }
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void globGroupIdMatchesProvider() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.*", "jackson-databind")),
          //language=xml
          pomXml(JACKSON_BEFORE, JACKSON_AFTER)
        );
    }

    @Test
    void singleCharacterWildcardMatchesProvider() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.cor?", "jackson-databin?")),
          //language=xml
          pomXml(JACKSON_BEFORE, JACKSON_AFTER)
        );
    }

    @Test
    void wildcardArtifactIdRemovesDependenciesProvidedBySibling() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "com.fasterxml.jackson.core", "*")),
          //language=xml
          pomXml(JACKSON_BEFORE, JACKSON_AFTER)
        );
    }

    @Test
    void matchAllWildcardsRemoveEveryTransitivelyProvidedDependency() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies("*", "*")),
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-databind</artifactId>
                    <version>2.17.0</version>
                  </dependency>
                  <dependency>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-core</artifactId>
                    <version>2.17.0</version>
                  </dependency>
                  <dependency>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-annotations</artifactId>
                    <version>2.17.0</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-databind</artifactId>
                    <version>2.17.0</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void matchAllWildcardsKeepDependenciesNobodyElseProvides() {
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies("*", "*")),
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-databind</artifactId>
                    <version>2.17.0</version>
                  </dependency>
                  <dependency>
                    <groupId>org.apache.commons</groupId>
                    <artifactId>commons-lang3</artifactId>
                    <version>3.14.0</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void removesStarterMatchedByTheSameGlobAsItsProvider() {
        // spring-boot-starter-web transitively provides spring-boot-starter-json. Both match the
        // `spring-boot-starter-*` glob, so the redundant one must still be removed.
        rewriteRun(
          spec -> spec.recipe(new RemoveRedundantDependencies(
            "org.springframework.boot", "spring-boot-starter-*")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-json</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.3</version>
                    <relativePath/>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void removeRedundantGradleDependencyWithWildcardArtifactId() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi())
            .recipe(new RemoveRedundantDependencies(
              "com.fasterxml.jackson.core", "*")),
          mavenProject("my-app",
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
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
                    implementation 'com.fasterxml.jackson.core:jackson-core:2.17.0'
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
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
                }
                """
            )
          )
        );
    }

    private static final String JACKSON_BEFORE = """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.mycompany.app</groupId>
        <artifactId>my-app</artifactId>
        <version>1</version>
        <dependencies>
          <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
          </dependency>
          <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>2.17.0</version>
          </dependency>
        </dependencies>
      </project>
      """;

    private static final String JACKSON_AFTER = """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.mycompany.app</groupId>
        <artifactId>my-app</artifactId>
        <version>1</version>
        <dependencies>
          <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
          </dependency>
        </dependencies>
      </project>
      """;
}
