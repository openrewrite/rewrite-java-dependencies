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
package org.openrewrite.java.dependencies.search;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
class HasDependencyMatcher {
    private final String groupIdPattern;
    private final String artifactIdPattern;
    private final @Nullable String scope;
    private final @Nullable String version;
    private final boolean onlyDirect;

    boolean hasDependency(Tree tree) {
        MavenResolutionResult mavenResult = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (mavenResult != null) {
            return hasMavenDependency(mavenResult, versionComparator());
        }
        GradleProject gp = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
        return gp != null && hasGradleDependency(gp, versionComparator());
    }

    private @Nullable VersionComparator versionComparator() {
        return version != null ? Semver.validate(version, null).getValue() : null;
    }

    private boolean hasMavenDependency(MavenResolutionResult mavenResult, @Nullable VersionComparator versionComparator) {
        Scope requestedScope = scope == null ? null : Scope.fromName(scope);
        List<ResolvedDependency> dependencies = mavenResult.findDependencies(groupIdPattern, artifactIdPattern, requestedScope);
        Set<String> resolvedGAs = new HashSet<>();
        for (ResolvedDependency dependency : dependencies) {
            if (onlyDirect && !dependency.isDirect()) {
                continue;
            }
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

    private boolean hasGradleDependency(GradleProject gp, @Nullable VersionComparator versionComparator) {
        Set<String> resolvedGAs = new HashSet<>();
        for (GradleDependencyConfiguration c : gp.getConfigurations()) {
            for (ResolvedDependency resolvedDependency : c.getDirectResolved()) {
                ResolvedDependency found = findMatching(resolvedDependency);
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
        return false;
    }

    private @Nullable ResolvedDependency findMatching(ResolvedDependency root) {
        if (onlyDirect) {
            if (StringUtils.matchesGlob(root.getGroupId(), groupIdPattern) &&
                    StringUtils.matchesGlob(root.getArtifactId(), artifactIdPattern)) {
                return root;
            }
            return null;
        }
        return root.findDependency(groupIdPattern, artifactIdPattern);
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
}
