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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveExclusionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveExclusion("commons-logging", "commons-logging"));
    }

    @DocumentExample("Remove an exclusion from a Gradle dependency")
    @Test
    void removeGradleExclusion() {
        rewriteRun(
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
                  implementation('org.apache.httpcomponents:httpclient:4.5.13') {
                      exclude group: 'commons-logging', module: 'commons-logging'
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
                  implementation('org.apache.httpcomponents:httpclient:4.5.13')
              }
              """
          )
        );
    }

    @Test
    void removeGradleKotlinDslExclusion() {
        rewriteRun(
          //language=kotlin
          buildGradleKts(
            """
              plugins {
                  `java-library`
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation("org.apache.httpcomponents:httpclient:4.5.13") {
                      exclude(group = "commons-logging", module = "commons-logging")
                  }
              }
              """,
            """
              plugins {
                  `java-library`
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation("org.apache.httpcomponents:httpclient:4.5.13")
              }
              """
          )
        );
    }

    @Test
    void removeGradleConfigurationExclusion() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }

              configurations.all {
                  exclude group: 'commons-logging', module: 'commons-logging'
                  exclude group: 'org.slf4j', module: 'slf4j-simple'
              }
              """,
            """
              plugins {
                  id 'java-library'
              }

              configurations.all {
                  exclude group: 'org.slf4j', module: 'slf4j-simple'
              }
              """
          )
        );
    }

    @Test
    void removeMavenExclusion() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.apache.httpcomponents</groupId>
                    <artifactId>httpclient</artifactId>
                    <version>4.5.13</version>
                    <exclusions>
                      <exclusion>
                        <groupId>commons-logging</groupId>
                        <artifactId>commons-logging</artifactId>
                      </exclusion>
                    </exclusions>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.apache.httpcomponents</groupId>
                    <artifactId>httpclient</artifactId>
                    <version>4.5.13</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void keepOtherMavenExclusions() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.httpcomponents</groupId>
                      <artifactId>httpclient</artifactId>
                      <version>4.5.13</version>
                      <exclusions>
                        <exclusion>
                          <groupId>commons-logging</groupId>
                          <artifactId>commons-logging</artifactId>
                        </exclusion>
                        <exclusion>
                          <groupId>commons-codec</groupId>
                          <artifactId>commons-codec</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.httpcomponents</groupId>
                      <artifactId>httpclient</artifactId>
                      <version>4.5.13</version>
                      <exclusions>
                        <exclusion>
                          <groupId>commons-codec</groupId>
                          <artifactId>commons-codec</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void onlyArtifactId() {
        rewriteRun(
          spec -> spec.recipe(new RemoveExclusion(null, "commons-logging")),
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
                  implementation('org.apache.httpcomponents:httpclient:4.5.13') {
                      exclude group: 'commons-logging', module: 'commons-logging'
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
                  implementation('org.apache.httpcomponents:httpclient:4.5.13')
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.apache.httpcomponents</groupId>
                    <artifactId>httpclient</artifactId>
                    <version>4.5.13</version>
                    <exclusions>
                      <exclusion>
                        <groupId>commons-logging</groupId>
                        <artifactId>commons-logging</artifactId>
                      </exclusion>
                    </exclusions>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>org.apache.httpcomponents</groupId>
                    <artifactId>httpclient</artifactId>
                    <version>4.5.13</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void groupOrArtifactIsRequired() {
        assertThat(new RemoveExclusion(null, null).validate().isValid()).isFalse();
        assertThat(new RemoveExclusion("*", "*").validate().isValid()).isFalse();
    }

    @Test
    void partialWildcardIsInvalid() {
        assertThat(new RemoveExclusion("commons-*", "commons-logging").validate().isValid()).isFalse();
    }
}
