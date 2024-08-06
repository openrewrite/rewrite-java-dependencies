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
package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

@SuppressWarnings("GroovyUnusedAssignment")
class SoftwareBillOfMaterialsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SoftwareBillOfMaterials());
    }

    @Test
    void maven() {
        rewriteRun(
          //language=xml
          pomXml("""
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                  <dependency>
                      <groupId>org.yaml</groupId>
                      <artifactId>snakeyaml</artifactId>
                      <version>1.27</version>
                  </dependency>
                  <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter</artifactId>
                    <version>5.7.0</version>
                    <scope>test</scope>
                  </dependency>
                </dependencies>
              </project>
            """),
          xml(null,
            //language=xml
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <bom xmlns="http://cyclonedx.org/schema/bom/1.6" version="1">
                <metadata>
                  <tools>
                    <tool>
                      <vendor>OpenRewrite by Moderne</vendor>
                      <name>OpenRewrite CycloneDX</name>
                      <version>8.32.0</version>
                    </tool>
                  </tools>
                  <component bom-ref="pkg:maven/com.mycompany.app/my-app@1">
                    <group>com.mycompany.app</group>
                    <name>my-app</name>
                    <version>1</version>
                    <purl>pkg:maven/com.mycompany.app/my-app@1</purl>
                  </component>
                </metadata>
                <components>
                  <component bom-ref="pkg:maven/org.yaml/snakeyaml@1.27">
                    <group>org.yaml</group>
                    <name>snakeyaml</name>
                    <version>1.27</version>
                    <scope>required</scope>
                    <licenses>
                      <license>
                        <name>Apache License, Version 2.0</name>
                      </license>
                    </licenses>
                    <purl>pkg:maven/org.yaml/snakeyaml@1.27</purl>
                  </component>
                </components>
                <dependencies>
                  <dependency ref="pkg:maven/org.yaml/snakeyaml@1.27"/>
                </dependencies>
              </bom>
              """,
            spec -> spec.path("sbom.xml"))
        );
    }


    @Test
    void gradle() {
        // GradlePlugin marker seems to be missing license information
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("root",
            settingsGradle("include 'my-app'"),
            mavenProject("my-app",
              //language=groovy
              buildGradle("""
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                group = "com.mycompany.app"
                version = "1"
                dependencies {
                    implementation("org.yaml:snakeyaml:1.27")
                }
                """)
              ,
              xml(null,
                //language=xml
                """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <bom xmlns="http://cyclonedx.org/schema/bom/1.6" version="1">
                    <metadata>
                      <tools>
                        <tool>
                          <vendor>OpenRewrite by Moderne</vendor>
                          <name>OpenRewrite CycloneDX</name>
                          <version>8.32.0</version>
                        </tool>
                      </tools>
                      <component bom-ref="pkg:maven/com.mycompany.app/my-app@1">
                        <group>com.mycompany.app</group>
                        <name>my-app</name>
                        <version>1</version>
                        <purl>pkg:maven/com.mycompany.app/my-app@1</purl>
                      </component>
                    </metadata>
                    <components>
                      <component bom-ref="pkg:maven/org.yaml/snakeyaml@1.27">
                        <group>org.yaml</group>
                        <name>snakeyaml</name>
                        <version>1.27</version>
                        <scope>required</scope>
                        <licenses/>
                        <purl>pkg:maven/org.yaml/snakeyaml@1.27</purl>
                      </component>
                    </components>
                    <dependencies>
                      <dependency ref="pkg:maven/org.yaml/snakeyaml@1.27"/>
                    </dependencies>
                  </bom>
                  """,
                spec -> spec.path("sbom.xml"))
            )));
    }
}
