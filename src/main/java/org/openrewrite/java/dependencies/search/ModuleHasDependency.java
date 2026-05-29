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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class ModuleHasDependency extends ScanningRecipe<Set<JavaProject>> {

    String displayName = "Module has dependency";

    String description = "Searches for both Gradle and Maven modules that have a dependency matching the specified groupId and artifactId. " +
                "Places a `SearchResult` marker on all sources within a module with a matching dependency. " +
                "This recipe is intended to be used as a precondition for other recipes. " +
                "For example this could be used to limit the application of a spring boot migration to only projects " +
                "that use spring-boot-starter, limiting unnecessary upgrading. " +
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

    @Option(displayName = "Invert marking",
            description = "If `true`, will invert the check for whether to mark a file. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean invertMarking;

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                tree.getMarkers()
                        .findFirst(JavaProject.class)
                        .ifPresent(jp -> {
                            if (!acc.contains(jp) && hasDependency(tree)) {
                                acc.add(jp);
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
            Set<String> resolvedGAs = new HashSet<>();
            for (ResolvedDependency dependency : dependencies) {
                resolvedGAs.add(dependency.getGroupId() + ":" + dependency.getArtifactId());
                if (versionComparator == null || versionComparator.isValid(null, dependency.getVersion())) {
                    return true;
                }
            }
            for (Dependency requested : mavenResult.getPom().getRequestedDependencies()) {
                if (resolvedGAs.contains(requested.getGroupId() + ":" + requested.getArtifactId())) {
                    continue;
                }
                if (matchesRequested(requested, requestedScope, versionComparator)) {
                    return true;
                }
            }
            return false;
        }

        GradleProject gp = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
        if (gp != null) {
            Set<String> resolvedGAs = new HashSet<>();
            for (GradleDependencyConfiguration c : gp.getConfigurations()) {
                for (ResolvedDependency resolvedDependency : c.getDirectResolved()) {
                    ResolvedDependency found = resolvedDependency.findDependency(groupIdPattern, artifactIdPattern);
                    if (found != null) {
                        resolvedGAs.add(found.getGroupId() + ":" + found.getArtifactId());
                        if (versionComparator == null || versionComparator.isValid(null, found.getVersion())) {
                            return true;
                        }
                    }
                }
            }
            for (GradleDependencyConfiguration c : gp.getConfigurations()) {
                for (Dependency requested : c.getRequested()) {
                    if (resolvedGAs.contains(requested.getGroupId() + ":" + requested.getArtifactId())) {
                        continue;
                    }
                    if (matchesRequested(requested, null, versionComparator)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesRequested(Dependency dep, @Nullable Scope requestedScope, @Nullable VersionComparator versionComparator) {
        if (dep.getGroupId() == null || dep.getArtifactId() == null) {
            return false;
        }
        if (!StringUtils.matchesGlob(dep.getGroupId(), groupIdPattern)) {
            return false;
        }
        if (!StringUtils.matchesGlob(dep.getArtifactId(), artifactIdPattern)) {
            return false;
        }
        if (requestedScope != null) {
            Scope depScope = dep.getScope() == null ? Scope.Compile : Scope.fromName(dep.getScope());
            if (!depScope.isInClasspathOf(requestedScope)) {
                return false;
            }
        }
        return versionMatches(dep.getVersion(), versionComparator);
    }

    private static boolean versionMatches(@Nullable String version, @Nullable VersionComparator cmp) {
        if (cmp == null) {
            return true;
        }
        if (version == null || version.startsWith("${")) {
            return false;
        }
        return cmp.isValid(null, version);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                boolean shouldInvert = invertMarking != null && invertMarking;
                String dependencyGav = groupIdPattern + ":" + artifactIdPattern + (version == null ? "" : ":" + version);
                Optional<JavaProject> maybeJp = tree.getMarkers().findFirst(JavaProject.class);
                if (!maybeJp.isPresent()) {
                    if (shouldInvert) {
                        return SearchResult.found(tree, "No module, so vacuously does not have dependency: " + dependencyGav);
                    }
                    return tree;
                }
                JavaProject jp = maybeJp.get();
                if (shouldInvert && !acc.contains(jp)) {
                    return SearchResult.found(tree, "Module does not have dependency: " + dependencyGav);
                }
                if (!shouldInvert && acc.contains(jp)) {
                    return SearchResult.found(tree, "Module has dependency: " + dependencyGav);
                }
                return tree;
            }
        };
    }
}
