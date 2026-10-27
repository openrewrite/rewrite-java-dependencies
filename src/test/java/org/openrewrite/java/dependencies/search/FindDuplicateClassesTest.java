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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.dependencies.table.DuplicateClassesReport;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class FindDuplicateClassesTest implements RewriteTest {

    private static final JavaProject PROJECT = new JavaProject(Tree.randomId(), "test-project", null);

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDuplicateClasses());
    }

    @DocumentExample
    @Test
    void findsDuplicateSlf4jBindingClasses() {
        JavaSourceSet sourceSet = JavaSourceSet.build("main", classpath("logback-classic-1.2", "slf4j-nop-1.7"));
        rewriteRun(
          spec -> spec.dataTable(DuplicateClassesReport.Row.class, rows -> {
              assertThat(rows)
                .allSatisfy(row -> {
                    assertThat(row.getProjectName()).isEqualTo("test-project");
                    assertThat(row.getSourceSet()).isEqualTo("main");
                    assertThat(List.of(row.getDependency1(), row.getDependency2()))
                      .satisfiesExactlyInAnyOrder(
                        gav -> assertThat(gav).contains("logback-classic"),
                        gav -> assertThat(gav).contains("slf4j-nop"));
                    assertThat(row.getAdditionalDependencies()).isEmpty();
                })
                .extracting(DuplicateClassesReport.Row::getTypeName)
                .contains("org.slf4j.impl.StaticLoggerBinder")
                .doesNotHaveDuplicates();
          }),
          java(
            """
              class A {
              }
              """,
            spec -> spec.markers(sourceSet, PROJECT)
          )
        );
    }

    @Test
    void reportsEachSourceSetOnce() {
        JavaSourceSet sourceSet = JavaSourceSet.build("main", classpath("logback-classic-1.2", "slf4j-nop-1.7"));
        rewriteRun(
          spec -> spec.dataTable(DuplicateClassesReport.Row.class, rows ->
            assertThat(rows)
              .extracting(DuplicateClassesReport.Row::getTypeName)
              .doesNotHaveDuplicates()),
          java(
            """
              class A {
              }
              """,
            spec -> spec.markers(sourceSet, PROJECT)
          ),
          java(
            """
              class B {
              }
              """,
            spec -> spec.markers(sourceSet, PROJECT)
          )
        );
    }

    @Test
    void noDuplicatesWithSingleDependency() {
        JavaSourceSet sourceSet = JavaSourceSet.build("main", classpath("guava-33"));
        rewriteRun(
          spec -> spec.afterRecipe(run ->
            assertThat(run.getDataTableRows(DuplicateClassesReport.class)).isEmpty()),
          java(
            """
              class A {
              }
              """,
            spec -> spec.markers(sourceSet, PROJECT)
          )
        );
    }

    @Test
    void noDuplicatesWithoutSourceSet() {
        rewriteRun(
          spec -> spec.afterRecipe(run ->
            assertThat(run.getDataTableRows(DuplicateClassesReport.class)).isEmpty()),
          java(
            """
              class A {
              }
              """
          )
        );
    }

    private static List<Path> classpath(String... artifactNamesWithVersions) {
        return JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), artifactNamesWithVersions);
    }
}
