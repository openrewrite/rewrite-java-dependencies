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
package org.openrewrite.java.dependencies.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class FindBillOfMaterialsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBillOfMaterials());
    }

    @DocumentExample
    @Test
    void minorUpgradeMaven() {
        rewriteRun(
          spec ->
            spec
              .dataTable(DependenciesInUse.Row.class, rows ->
                assertThat(rows).containsExactly(
                  new DependenciesInUse.Row(
                    "code-with-quarkus",
                    "main",
                    "io.quarkus.platform",
                    "quarkus-bom",
                    "3.25.0",
                    null,
                    "import",
                    0
                  )
                )
              ),
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.acme</groupId>
                <artifactId>code-with-quarkus</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <properties>
                  <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
                  <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
                  <quarkus.platform.version>3.25.0</quarkus.platform.version>
                </properties>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>${quarkus.platform.group-id}</groupId>
                      <artifactId>${quarkus.platform.artifact-id}</artifactId>
                      <version>${quarkus.platform.version}</version>
                      <type>pom</type>
                      <scope>import</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.acme</groupId>
                <artifactId>code-with-quarkus</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <properties>
                  <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
                  <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
                  <quarkus.platform.version>3.25.0</quarkus.platform.version>
                </properties>
                <dependencyManagement>
                  <dependencies>
                    <!--~~>--><dependency>
                      <groupId>${quarkus.platform.group-id}</groupId>
                      <artifactId>${quarkus.platform.artifact-id}</artifactId>
                      <version>${quarkus.platform.version}</version>
                      <type>pom</type>
                      <scope>import</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void minorUpgradeGradleKts() {
        rewriteRun(
          spec ->
            spec
              .beforeRecipe(withToolingApi())
              .dataTable(DependenciesInUse.Row.class, rows ->
                assertThat(rows).containsExactly(
                  new DependenciesInUse.Row(
                    "code-with-quarkus",
                    "main",
                    "io.quarkus.platform",
                    "quarkus-bom",
                    "3.25.0",
                    null,
                    "import",
                    0
                  )
                )
              ),
          //language=kts
          buildGradleKts(
            """
              plugins {
                  java
                  id("io.quarkus")
              }

              repositories {
                  mavenCentral()
                  mavenLocal()
              }

              val quarkusPlatformGroupId: String by project
              val quarkusPlatformArtifactId: String by project
              val quarkusPlatformVersion: String by project

              dependencies {
                  implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
                  implementation("io.quarkus:quarkus-arc")
                  implementation("io.quarkus:quarkus-rest")
                  testImplementation("io.quarkus:quarkus-junit5")
                  testImplementation("io.rest-assured:rest-assured")
              }

              group = "org.acme"
              version = "1.0.0-SNAPSHOT"
              """
          )
        );
    }
}
