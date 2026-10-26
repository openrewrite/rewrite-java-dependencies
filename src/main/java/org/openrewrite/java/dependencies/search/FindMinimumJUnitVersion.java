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
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonMap;
import static org.openrewrite.java.dependencies.search.FindMinimumDependencyVersion.OLDEST_FIRST;
import static org.openrewrite.java.dependencies.search.FindMinimumDependencyVersion.applyMarkersForLocatedGavs;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindMinimumJUnitVersion extends Recipe {
    private static final GroupArtifact JUNIT_4 = new GroupArtifact("junit", "junit");
    private static final GroupArtifact JUNIT_JUPITER_API = new GroupArtifact("org.junit.jupiter", "junit-jupiter-api");

    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Option(displayName = "Version",
            description = "Only report modules whose minimum JUnit major version is the provided version. " +
                          "If a module uses several JUnit major versions, the lowest one is its minimum version.",
            example = "4",
            valid = {"4", "5", "6"},
            required = false)
    @Nullable
    String minimumVersion;

    String displayName = "Find minimum JUnit version";

    String description = "Finds the minimum version of JUnit used by each module, " +
               "looking at `junit:junit` for JUnit 4 and `org.junit.jupiter:junit-jupiter-api` for JUnit 5 and 6. " +
               "If a module uses several JUnit major versions, the lowest one is reported.\n" +
               "If a `minimumVersion` is provided, the recipe only reports modules for which " +
               "that is the minimum JUnit major version.\n" +
               "For example: if the `minimumVersion` is 4, and a module has JUnit 4.12 and JUnit 5.7, " +
               "the recipe will report JUnit 4.12. If a module has only JUnit 5.7, the recipe will not report it.\n" +
               "Another example: if the `minimumVersion` is 5, and a module has JUnit 4.12 and JUnit 5.7, " +
               "the recipe will not report it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                ResolvedGroupArtifactVersion minimum = findMinimumJUnit(tree.getMarkers());
                if (minimum == null || !isRequestedMajorVersion(minimum)) {
                    return tree;
                }
                return applyMarkersForLocatedGavs(
                        singletonMap(new GroupArtifact(minimum.getGroupId(), minimum.getArtifactId()), minimum),
                        dependenciesInUse).visit(tree, ctx);
            }
        };
    }

    private boolean isRequestedMajorVersion(ResolvedGroupArtifactVersion gav) {
        if (minimumVersion == null) {
            return true;
        }
        return minimumVersion.equals(gav.getVersion().split("\\D", 2)[0]);
    }

    private static @Nullable ResolvedGroupArtifactVersion findMinimumJUnit(Markers markers) {
        List<List<ResolvedDependency>> resolved = new ArrayList<>();
        markers.findFirst(GradleProject.class).ifPresent(gradle -> {
            for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                resolved.add(conf.getResolved());
            }
        });
        markers.findFirst(MavenResolutionResult.class).ifPresent(maven -> resolved.addAll(maven.getDependencies().values()));

        ResolvedGroupArtifactVersion minimum = null;
        for (List<ResolvedDependency> dependencies : resolved) {
            for (ResolvedDependency dep : dependencies) {
                GroupArtifact ga = new GroupArtifact(dep.getGroupId(), dep.getArtifactId());
                if ((JUNIT_4.equals(ga) || JUNIT_JUPITER_API.equals(ga)) &&
                    (minimum == null || OLDEST_FIRST.compare(dep.getGav(), minimum) < 0)) {
                    minimum = dep.getGav();
                }
            }
        }
        return minimum;
    }
}
