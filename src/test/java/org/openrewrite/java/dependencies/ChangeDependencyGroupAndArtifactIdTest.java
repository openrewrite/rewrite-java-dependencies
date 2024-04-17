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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class ChangeDependencyGroupAndArtifactIdTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeDependencyGroupIdAndArtifactId("io.swagger", "swagger-core",
          "io.swagger.core.v3", null, "2.2.21", null, null, null));
    }

    @DocumentExample
    @Test
    void gradle() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          //language=groovy
          buildGradle(
            """
               plugins {
                   id "java-library"
               }
               
               repositories {
                   mavenCentral()
               }
               
               dependencies {
                   implementation "io.swagger:swagger-core:1.6.14"
               }
               """,
            """
               plugins {
                   id "java-library"
               }
               
               repositories {
                   mavenCentral()
               }
               
               dependencies {
                   implementation "io.swagger.core.v3:swagger-core:2.2.21"
               }
               """
          )
        );
    }

    @Test
    void maven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
               <project>
                   <modelVersion>4.0.0</modelVersion>
                   <groupId>org.example</groupId>
                   <artifactId>example</artifactId>
                   <version>1.0-SNAPSHOT</version>
                   <dependencies>
                       <dependency>
                           <groupId>io.swagger</groupId>
                           <artifactId>swagger-core</artifactId>
                           <version>1.6.14</version>
                       </dependency>
                   </dependencies>
               </project>
               """,
            """
               <project>
                   <modelVersion>4.0.0</modelVersion>
                   <groupId>org.example</groupId>
                   <artifactId>example</artifactId>
                   <version>1.0-SNAPSHOT</version>
                   <dependencies>
                       <dependency>
                           <groupId>io.swagger.core.v3</groupId>
                           <artifactId>swagger-core</artifactId>
                           <version>2.2.21</version>
                       </dependency>
                   </dependencies>
               </project>
               """)
        );
    }

}
