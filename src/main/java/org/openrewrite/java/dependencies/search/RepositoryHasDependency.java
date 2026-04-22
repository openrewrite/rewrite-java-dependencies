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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class RepositoryHasDependency extends ScanningRecipe<AtomicBoolean> {

    String displayName = "Repository has dependency";

    String description = "Searches for both Gradle and Maven modules that have a dependency matching the specified groupId and artifactId. " +
               "Places a `SearchResult` marker on all sources within a repository with a matching dependency. " +
               "This recipe is intended to be used as a precondition for other recipes. " +
               "For example this could be used to limit the application of a spring boot migration to only projects " +
               "that use a springframework dependency, limiting unnecessary upgrading. " +
               "If the search result you want is instead just the build.gradle(.kts) or pom.xml file applying the plugin, use the `FindDependency` recipe instead.";

    @Option(displayName = "Group pattern",
            description = "Group glob pattern used to match dependencies.",
            example = "com.fasterxml.jackson.module")
    String groupIdPattern;

    @Option(displayName = "Artifact pattern",
            description = "Artifact glob pattern used to match dependencies.",
            example = "jackson-module-*")
    String artifactIdPattern;

    @Option(displayName = "Scope",
            description = "Match dependencies with the specified scope. All scopes are searched by default.",
            valid = {"compile", "test", "runtime", "provided", "system"},
            example = "compile",
            required = false)
    @Nullable
    String scope;

    @Option(displayName = "Version",
            description = "Match only dependencies with the specified version. " +
                          "Node-style [version selectors](https://docs.openrewrite.org/reference/dependency-version-selectors) may be used." +
                          "All versions are searched by default.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                if (acc.get()) {
                    return tree;
                }
                tree.getMarkers()
                        .findFirst(JavaProject.class)
                        .ifPresent(jp -> {
                            if (hasDependency(tree)) {
                                acc.set(true);
                            }
                        });
                return tree;
            }
        };
    }

    private boolean hasDependency(Tree tree) {
        VersionComparator versionComparator = version != null ? Semver.validate(version, null).getValue() : null;

        MavenResolutionResult mavenResult = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (mavenResult != null) {
            Scope requestedScope = scope == null ? null : Scope.fromName(scope);
            List<ResolvedDependency> dependencies = mavenResult.findDependencies(groupIdPattern, artifactIdPattern, requestedScope);
            for (ResolvedDependency dependency : dependencies) {
                if (versionComparator == null || versionComparator.isValid(null, dependency.getVersion())) {
                    return true;
                }
            }
            return false;
        }

        GradleProject gp = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
        if (gp != null) {
            for (GradleDependencyConfiguration c : gp.getConfigurations()) {
                for (ResolvedDependency resolvedDependency : c.getDirectResolved()) {
                    ResolvedDependency found = resolvedDependency.findDependency(groupIdPattern, artifactIdPattern);
                    if (found != null && (versionComparator == null || versionComparator.isValid(null, found.getVersion()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean acc) {
        if (acc.get()) {
            return new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    assert tree != null;
                    return SearchResult.found(tree, "Repository has dependency: " + groupIdPattern + ":" + artifactIdPattern + (version == null ? "" : ":" + version));
                }
            };
        }
        return TreeVisitor.noop();
    }
}
