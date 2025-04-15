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
package org.openrewrite.java.dependencies.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.dependencies.internal.StaticVersionComparator;
import org.openrewrite.java.dependencies.internal.VersionParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.search.FindMavenProject;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.maven.tree.*;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

import java.util.*;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindMinimumDependencyVersion extends ScanningRecipe<Map<GroupArtifact, ResolvedGroupArtifactVersion>> {
    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Option(displayName = "Group pattern",
            description = "Group ID glob pattern used to match dependencies.",
            example = "com.fasterxml.jackson.module")
    String groupIdPattern;

    @Option(displayName = "Artifact pattern",
            description = "Artifact ID glob pattern used to match dependencies.",
            example = "jackson-module-*")
    String artifactIdPattern;

    @Option(displayName = "Version",
            description = "Match only dependencies with the specified version. " +
                          "Node-style [version selectors](https://docs.openrewrite.org/reference/dependency-version-selectors) may be used. " +
                          "All versions are searched by default.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Override
    public String getDisplayName() {
        return "Find the oldest matching dependency version in use";
    }

    @Override
    public String getDescription() {
        return "The oldest dependency version in use is the lowest dependency " +
               "version in use in any source set of any subproject of " +
               "a repository. It is possible that, for example, the main " +
               "source set of a project uses Jackson 2.11, but a test source set " +
               "uses Jackson 2.16. In this case, the oldest Jackson version in use is " +
               "Java 2.11.";
    }

    @Override
    public Map<GroupArtifact, ResolvedGroupArtifactVersion> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                VersionParser versionParser = new VersionParser();
                Markers m = tree.getMarkers();
                m.findFirst(GradleProject.class).ifPresent(gradle -> {
                    for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                        collectMinimumVersions(versionParser, conf.getResolved(), acc);
                    }
                });
                m.findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    for (List<ResolvedDependency> resolved : maven.getDependencies().values()) {
                        collectMinimumVersions(versionParser, resolved, acc);
                    }
                });
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
        VersionComparator versionComparator = version == null ? null :
                requireNonNull(Semver.validate(version, null).getValue());
        StaticVersionComparator staticVersionComparator = new StaticVersionComparator();
        VersionParser versionParser = new VersionParser();
        String minimumVersion = acc.values().stream().map(ResolvedGroupArtifactVersion::getVersion)
                .min((d1, d2) -> staticVersionComparator.compare(
                        versionParser.transform(d1),
                        versionParser.transform(d2)))
                .filter(min -> versionComparator == null || versionComparator.isValid(null, min))
                .orElse(null);
        if (minimumVersion == null) {
            return TreeVisitor.noop();
        }

        acc.entrySet().removeIf(e -> !e.getValue().getVersion().equals(minimumVersion));

        return applyMarkersForLocatedGavs(acc, dependenciesInUse);
    }

    static TreeVisitor<?, ExecutionContext> applyMarkersForLocatedGavs(Map<GroupArtifact, ResolvedGroupArtifactVersion> acc, DependenciesInUse dependenciesInUse) {
        return Preconditions.check(Preconditions.or(new IsBuildGradle<>(), new FindMavenProject().getVisitor()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                Markers m = tree.getMarkers();
                Tree t = tree;
                t = m.findFirst(GradleProject.class).map(gradle -> {
                    Tree t2 = tree;
                    for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                        List<ResolvedDependency> resolved = conf.getResolved();
                        t2 = recordMinimumDependencyUse(ctx, gradle.getName(), conf.getName(), resolved, t2);
                    }
                    return t2;
                }).orElse(t);

                t = m.findFirst(MavenResolutionResult.class).map(maven -> {
                    Tree t2 = tree;
                    for (Map.Entry<Scope, List<ResolvedDependency>> resolved : maven.getDependencies().entrySet()) {
                        t2 = recordMinimumDependencyUse(ctx, maven.getPom().getArtifactId(),
                                resolved.getKey().toString().toLowerCase(), resolved.getValue(), t2);
                    }
                    return t2;
                }).orElse(t);

                return t;
            }

            private Tree recordMinimumDependencyUse(ExecutionContext ctx, String projectName, String scope, List<ResolvedDependency> resolved, Tree t) {
                Set<String> minimums = new TreeSet<>();
                for (ResolvedDependency dep : resolved) {
                    for (ResolvedGroupArtifactVersion min : acc.values()) {
                        if (dep.getGav().equals(min)) {
                            minimums.add(dep.getGav().toString());
                            dependenciesInUse.insertRow(ctx, new DependenciesInUse.Row(
                                    projectName,
                                    t.getMarkers().findFirst(JavaSourceSet.class).map(JavaSourceSet::getName).orElse("unknown"),
                                    dep.getGroupId(),
                                    dep.getArtifactId(),
                                    dep.getVersion(),
                                    dep.getGav().getDatedSnapshotVersion(),
                                    scope,
                                    dep.getDepth()));
                        }
                    }
                }
                if (!minimums.isEmpty()) {
                    return SearchResult.found(t, String.join("\n", minimums));
                }
                return t;
            }
        });
    }

    private void collectMinimumVersions(VersionParser versionParser, List<ResolvedDependency> resolved,
                                        Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
        StaticVersionComparator versionComparator = new StaticVersionComparator();
        for (ResolvedDependency dep : resolved) {
            if (StringUtils.matchesGlob(dep.getGroupId(), groupIdPattern) &&
                StringUtils.matchesGlob(dep.getArtifactId(), artifactIdPattern)) {
                acc.merge(new GroupArtifact(dep.getGroupId(), dep.getArtifactId()),
                        dep.getGav(), (d1, d2) -> versionComparator.compare(
                                versionParser.transform(d1.getVersion()),
                                versionParser.transform(d2.getVersion())) < 0 ?
                                d1 : d2);
            }
        }
    }
}
