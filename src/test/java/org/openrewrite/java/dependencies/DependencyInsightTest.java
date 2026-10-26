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
import org.openrewrite.config.DataTableDescriptor;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.maven.table.ExplainDependenciesInUse;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class DependencyInsightTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyInsight("org.springframework*", "*", null, null));
    }

    @DocumentExample
    @Test
    void maven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>5.2.6.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>foo</artifactId>
                <version>1.0.0</version>

                <dependencies>
                  <!--~~(org.springframework:spring-core:5.2.6.RELEASE,org.springframework:spring-jcl:5.2.6.RELEASE)~~>--><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>5.2.6.RELEASE</version>
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
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation 'org.springframework:spring-core:5.2.6.RELEASE'
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
                  /*~~(org.springframework:spring-core:5.2.6.RELEASE,org.springframework:spring-jcl:5.2.6.RELEASE)~~>*/implementation 'org.springframework:spring-core:5.2.6.RELEASE'
              }
              """
          )
        );
    }

    @Test
    void instanceNameShowsWhatIsBeingSearchedFor() {
        assertThat(new DependencyInsight("com.fasterxml.jackson*", "jackson-*", null, null).getInstanceName())
          .isEqualTo("Gradle or Maven dependency insight `com.fasterxml.jackson*:jackson-*`");
    }

    @Test
    void instanceNameIncludesVersionWhenGiven() {
        assertThat(new DependencyInsight("com.fasterxml.jackson*", "jackson-*", "2.x", null).getInstanceName())
          .isEqualTo("Gradle or Maven dependency insight `com.fasterxml.jackson*:jackson-*:2.x`");
    }

    @Test
    void instanceNameOmitsTheSuffixWhenUnconfigured() {
        // The descriptor of an unconfigured recipe is rendered wherever recipes are listed;
        // it must not read "null:null".
        assertThat(new DependencyInsight(null, null, null, null).getInstanceName())
          .isEqualTo("Gradle or Maven dependency insight");
    }

    @Test
    void declaresDataTablesFilledByDelegates() {
        assertThat(new DependencyInsight("com.fasterxml.jackson*", "jackson-*", null, null).getDataTableDescriptors())
          .extracting(DataTableDescriptor::getName)
          .contains(DependenciesInUse.class.getName(), ExplainDependenciesInUse.class.getName());
    }

    @Test
    void invalidOptionsAreInvalid() {
        assertThat(new DependencyInsight("*", "*", null, null).validate().isValid()).isFalse();
        assertThat(new DependencyInsight("junit", "junit", "latest.bogus", null).validate().isValid()).isFalse();
        assertThat(new DependencyInsight("junit", "junit", null, "bogus").validate().isValid()).isFalse();
    }
}
