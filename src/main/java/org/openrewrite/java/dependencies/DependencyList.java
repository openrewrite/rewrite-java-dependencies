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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.table.DependencyListReport;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;

@Value
@EqualsAndHashCode(callSuper = true)
public class DependencyList extends Recipe {

    transient DependencyListReport report = new DependencyListReport(this);

    @Option(displayName = "Scope",
            description = "The scope of the dependencies to include in the report.",
            valid = {"Compile", "Runtime", "TestRuntime"},
            example = "Compile")
    Scope scope;

    @Option(displayName = "Include transitive dependencies",
            description = "Whether or not to include transitive dependencies in the report. " +
                    "Defaults to including only direct dependencies.",
            example = "true")
    boolean includeTransitive;

    @Override
    public String getDisplayName() {
        return "Dependency report";
    }

    @Override
    public String getDescription() {
        return "Emits a data table detailing all Gradle and Maven dependencies." +
                "This recipe makes no changes to any source file.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                Markers m = tree.getMarkers();
                m.findFirst(GradleProject.class).ifPresent(gradle -> {
                    GradleDependencyConfiguration conf = gradle.getConfiguration(scope.asGradleConfigurationName());
                    if (conf != null) {
                        for (ResolvedDependency dep : conf.getResolved()) {
                            insertDependency(ctx, gradle, dep, true);
                        }
                    }
                });
                m.findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    for (ResolvedDependency dep : maven.getDependencies().get(scope.asMavenScope())) {
                        insertDependency(ctx, maven, dep, true);
                    }
                });
                return tree;
            }
        };
    }

    private void insertDependency(ExecutionContext ctx, GradleProject gradle, ResolvedDependency dep, boolean direct) {
        report.insertRow(ctx, new DependencyListReport.Row(
                "Gradle",
                "",
                gradle.getName(),
                "",
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                direct
        ));
        if (includeTransitive) {
            for (ResolvedDependency transitive : dep.getDependencies()) {
                insertDependency(ctx, gradle, transitive, false);
            }
        }
    }

    private void insertDependency(ExecutionContext ctx, MavenResolutionResult maven, ResolvedDependency dep, boolean direct) {
        report.insertRow(ctx, new DependencyListReport.Row(
                "Maven",
                maven.getPom().getGroupId(),
                maven.getPom().getArtifactId(),
                maven.getPom().getVersion(),
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                direct
        ));
        if (includeTransitive) {
            for (ResolvedDependency transitive : dep.getDependencies()) {
                insertDependency(ctx, maven, transitive, false);
            }
        }
    }

    public enum Scope {
        Compile,
        Runtime,
        TestRuntime;

        public org.openrewrite.maven.tree.Scope asMavenScope() {
            switch (this) {
                case Compile:
                    return org.openrewrite.maven.tree.Scope.Compile;
                case Runtime:
                    return org.openrewrite.maven.tree.Scope.Runtime;
                case TestRuntime:
                    return org.openrewrite.maven.tree.Scope.Test;
                default:
                    throw new IllegalStateException("Unexpected value: " + this);
            }
        }

        public String asGradleConfigurationName() {
            switch (this) {
                case Compile:
                    return "compileClasspath";
                case Runtime:
                    return "runtimeClasspath";
                case TestRuntime:
                    return "testRuntimeClasspath";
                default:
                    throw new IllegalStateException("Unexpected value: " + this);
            }
        }
    }
}
