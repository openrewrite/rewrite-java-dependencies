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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.ExceptionUtils;
import org.openrewrite.java.dependencies.table.DependencyListReport;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.table.MavenMetadataFailures;
import org.openrewrite.maven.tree.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyMap;

@EqualsAndHashCode(callSuper = false)
@Value
public class DependencyList extends Recipe {

    transient DependencyListReport report = new DependencyListReport(this);
    transient MavenMetadataFailures metadataFailures = new MavenMetadataFailures(this);

    @Option(displayName = "Scope",
            description = "The scope of the dependencies to include in the report." +
                          "Defaults to \"Compile\"",
            valid = {"Compile", "Runtime", "TestRuntime"},
            required = false,
            example = "Compile")
    @Nullable
    Scope scope;

    @Option(displayName = "Include transitive dependencies",
            description = "Whether or not to include transitive dependencies in the report. " +
                          "Defaults to including only direct dependencies." +
                          "Defaults to false.",
            required = false,
            example = "true")
    boolean includeTransitive;

    @Option(displayName = "Validate dependencies are resolvable",
            description = "When enabled the recipe will attempt to download every dependency it encounters, reporting on any failures. " +
                          "This can be useful for identifying dependencies that have become unavailable since an LST was produced." +
                          "Defaults to false.",
            valid = {"true", "false"},
            required = false,
            example = "true")
    boolean validateResolvable;

    /**
     * Freestanding gradle script plugins get assigned the same GradleProject marker with the build script in the project.
     * Keep track of the ones which have been seen to minimize duplicate entries in the report.
     */
    transient Set<GroupArtifactVersion> seenGradleProjects = new HashSet<>();

    String displayName = "Dependency report";

    String description = "Emits a data table detailing all Gradle and Maven dependencies. " +
               "This recipe makes no changes to any source file.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                Markers m = tree.getMarkers();
                Set<ResolvedGroupArtifactVersion> seen = new HashSet<>();
                m.findFirst(GradleProject.class)
                        .filter(gradle -> seenGradleProjects.add(new GroupArtifactVersion(gradle.getGroup(), gradle.getName(), gradle.getVersion())))
                        .ifPresent(gradle -> {
                            GradleDependencyConfiguration conf = gradle.getConfiguration(scope().asGradleConfigurationName());
                            if (conf != null) {
                                for (ResolvedDependency dep : conf.getResolved()) {
                                    if (dep.getDepth() > 0) {
                                        continue;
                                    }
                                    insertDependency(ctx, gradle, seen, dep, true);
                                }
                            }
                        });
                m.findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    for (ResolvedDependency dep : maven.getDependencies().get(scope().asMavenScope())) {
                        if (dep.getDepth() > 0) {
                            continue;
                        }
                        insertDependency(ctx, maven, seen, dep, true);
                    }
                });
                return tree;
            }
        };
    }

    private Scope scope() {
        return scope == null ? Scope.Compile : scope;
    }

    private void insertDependency(ExecutionContext ctx, GradleProject gradle, Set<ResolvedGroupArtifactVersion> seen, ResolvedDependency dep, boolean direct) {
        if (!seen.add(dep.getGav())) {
            return;
        }
        String resolutionFailure = "";
        if (validateResolvable) {
            try {
                //noinspection DataFlowIssue
                metadataFailures.insertRows(ctx, () -> new MavenPomDownloader(
                        emptyMap(), ctx,
                        null,
                        null)
                        .downloadMetadata(new GroupArtifact(gradle.getGroup(), gradle.getName()), null, gradle.getMavenRepositories()));
            } catch (MavenDownloadingException e) {
                resolutionFailure = ExceptionUtils.sanitizeStackTrace(e, RecipeScheduler.class);
            }
        }
        report.insertRow(ctx, new DependencyListReport.Row(
                "Gradle",
                gradle.getGroup(),
                gradle.getName(),
                gradle.getVersion(),
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                direct,
                resolutionFailure
        ));
        if (includeTransitive) {
            for (ResolvedDependency transitive : dep.getDependencies()) {
                insertDependency(ctx, gradle, seen, transitive, false);
            }
        }
    }

    private void insertDependency(ExecutionContext ctx, MavenResolutionResult maven, Set<ResolvedGroupArtifactVersion> seen, ResolvedDependency dep, boolean direct) {
        if (!seen.add(dep.getGav())) {
            return;
        }
        String resolutionFailure = "";
        if (validateResolvable) {
            try {
                MavenExecutionContextView mctx = MavenExecutionContextView.view(ctx);
                metadataFailures.insertRows(ctx, () -> new MavenPomDownloader(
                        emptyMap(), ctx,
                        mctx.getSettings() == null ? maven.getMavenSettings() :
                                maven.getMavenSettings() == null ? mctx.getSettings() :
                                        mctx.getSettings().merge(maven.getMavenSettings()),
                        Optional.ofNullable(mctx.getSettings())
                                .map(MavenSettings::getActiveProfiles)
                                .map(MavenSettings.ActiveProfiles::getActiveProfiles)
                                .orElse(maven.getActiveProfiles()))
                        .downloadMetadata(new GroupArtifact(maven.getPom().getGroupId(), maven.getPom().getArtifactId()), null, maven.getPom().getRepositories()));
            } catch (MavenDownloadingException e) {
                resolutionFailure = ExceptionUtils.sanitizeStackTrace(e, RecipeScheduler.class);
            }
        }
        report.insertRow(ctx, new DependencyListReport.Row(
                "Maven",
                maven.getPom().getGroupId(),
                maven.getPom().getArtifactId(),
                maven.getPom().getVersion(),
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                direct,
                resolutionFailure
        ));
        if (includeTransitive) {
            for (ResolvedDependency transitive : dep.getDependencies()) {
                insertDependency(ctx, maven, seen, transitive, false);
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
