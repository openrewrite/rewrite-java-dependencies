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
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.dependencies.internal.StaticVersionComparator;
import org.openrewrite.java.dependencies.internal.VersionParser;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.openrewrite.java.dependencies.search.FindMinimumDependencyVersion.applyMarkersForLocatedGavs;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindMinimumJUnitVersion extends ScanningRecipe<Map<GroupArtifact, ResolvedGroupArtifactVersion>> {
    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Option(displayName = "Version",
            description = "Determine if the provided version is the minimum JUnit version. " +
                          "If both JUnit 4 and JUnit 5 are present, the minimum version is JUnit 4. " +
                          "If only one version is present, that version is the minimum version.",
            example = "4",
            valid = {"4", "5"},
            required = false)
    @Nullable
    String minimumVersion;


    @Override
    public String getDisplayName() {
        return "Find minimum JUnit version";
    }

    @Override
    public String getDescription() {
        return "A recipe to find the minimum version of JUnit dependencies. " +
               "This recipe is designed to return the minimum version of JUnit in a project. " +
               "It will search for JUnit 4 and JUnit 5 dependencies in the project. " +
               "If both versions are found, it will return the minimum version of JUnit 4.\n" +
               "If a minimumVersion is provided, the recipe will search to see if " +
               "the minimum version of JUnit used by the project is no lower than the minimumVersion.\n" +
               "For example: if the minimumVersion is 4, and the project has JUnit 4.12 and JUnit 5.7, " +
               "the recipe will return JUnit 4.12. If the project has only JUnit 5.7, the recipe will return JUnit 5.7.\n" +
               "Another example: if the minimumVersion is 5, and the project has JUnit 4.12 and JUnit 5.7, " +
               "the recipe will not return any results.";
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
                        collectionJUnit4(versionParser, conf.getResolved(), acc);
                        collectionJUnit5(versionParser, conf.getResolved(), acc);
                    }
                });
                m.findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    for (List<ResolvedDependency> resolved : maven.getDependencies().values()) {
                        collectionJUnit4(versionParser, resolved, acc);
                        collectionJUnit5(versionParser, resolved, acc);
                    }
                });
                return tree;
            }
        };
    }

    private boolean isResolvedGroupArtifactVersion(ResolvedGroupArtifactVersion resolvedGroupArtifactVersion, String groupName, String artifactName) {
        return resolvedGroupArtifactVersion.getArtifactId().equals(artifactName) &&
               resolvedGroupArtifactVersion.getGroupId().equals(groupName);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
        boolean hasJUnit4 = acc.values().stream().anyMatch(resolvedGroupArtifactVersion -> isResolvedGroupArtifactVersion(resolvedGroupArtifactVersion, "junit", "junit"));
        boolean hasJUnit5 = acc.values().stream().anyMatch(resolvedGroupArtifactVersion -> isResolvedGroupArtifactVersion(resolvedGroupArtifactVersion, "org.junit.jupiter", "junit-jupiter-api"));
        if (Objects.equals(minimumVersion, "4")) {
            if (hasJUnit4) {
                acc.entrySet().removeIf(e -> !isResolvedGroupArtifactVersion(e.getValue(), "junit", "junit"));
            } else {
                return TreeVisitor.noop();
            }
        } else if (Objects.equals(minimumVersion, "5")) {
            if (hasJUnit4) {
                return TreeVisitor.noop();
            }
            acc.entrySet().removeIf(e -> !isResolvedGroupArtifactVersion(e.getValue(), "org.junit.jupiter", "junit-jupiter-api"));
        } else {
            if (hasJUnit4 && hasJUnit5) {
                acc.entrySet().removeIf(e -> !isResolvedGroupArtifactVersion(e.getValue(), "junit", "junit"));
            }
        }

        return applyMarkersForLocatedGavs(acc, dependenciesInUse);
    }

    private void collectionJUnit4(VersionParser versionParser, List<ResolvedDependency> resolved,
                                  Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
        collectVersion(versionParser, resolved, "junit", "junit", acc);
    }

    private void collectionJUnit5(VersionParser versionParser, List<ResolvedDependency> resolved,
                                  Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
        collectVersion(versionParser, resolved, "org.junit.jupiter", "junit-jupiter-api", acc);
    }

    private static void collectVersion(VersionParser versionParser, List<ResolvedDependency> resolved, String groupIdPattern, String artifactIdPattern, Map<GroupArtifact, ResolvedGroupArtifactVersion> acc) {
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
