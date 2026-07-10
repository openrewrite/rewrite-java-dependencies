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
package org.openrewrite.java.dependencies;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.MavenDownloadingExceptions;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.*;

import java.util.*;

import static java.util.Collections.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRedundantDependencies extends ScanningRecipe<RemoveRedundantDependencies.Accumulator> {

    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION` of the parent dependency. This can be a glob expression.",
            example = "com.fasterxml.jackson.core")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION` of the parent dependency. This can be a glob expression.",
            example = "jackson-databind")
    String artifactId;

    String displayName = "Remove redundant explicit dependencies";

    String description = "Remove explicit dependencies that are already provided transitively by a specified dependency. " +
                "This recipe downloads and resolves the parent dependency's POM to determine its true transitive " +
                "dependencies, allowing it to detect redundancies even when both dependencies are explicitly declared. " +
                "A direct dependency is only removed when the transitive one provides it at the exact same scope and " +
                "with the same exclusions, so that removing it does not change the effective classpath.";

    @Value
    public static class Accumulator {
        // Map from project identifier -> scope/configuration -> Set of transitive dependencies
        Map<String, Map<String, Set<TransitiveDependency>>> transitivesByProjectAndScope;
    }

    @Value
    public static class TransitiveDependency {
        ResolvedGroupArtifactVersion gav;
        Set<GroupArtifact> exclusions;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(new HashMap<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }

                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gradle -> {
                    String projectId = gradle.getGroup() + ":" + gradle.getName();
                    MavenPomDownloader downloader = new MavenPomDownloader(ctx);

                    for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                        for (ResolvedDependency dep : conf.getResolved()) {
                            if (dep.isDirect() &&
                                    StringUtils.matchesGlob(dep.getGroupId(), groupId) &&
                                    StringUtils.matchesGlob(dep.getArtifactId(), artifactId)) {
                                // This is a matching parent dependency, resolve its transitives independently
                                Set<TransitiveDependency> transitives = acc.transitivesByProjectAndScope
                                        .computeIfAbsent(projectId, k -> new HashMap<>())
                                        .computeIfAbsent(conf.getName(), k -> new HashSet<>());
                                resolveTransitivesFromPom(
                                        dep.getGav(),
                                        dep.getEffectiveExclusions(),
                                        gradle.getMavenRepositories(),
                                        downloader,
                                        ctx,
                                        transitives);
                            }
                        }
                    }
                });

                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    String projectId = maven.getPom().getGroupId() + ":" + maven.getPom().getArtifactId();
                    MavenPomDownloader downloader = new MavenPomDownloader(ctx);

                    // A direct dependency appears under every scope bucket it is visible in, so process
                    // each matching parent once, keyed by its own effective (declared) scope.
                    Set<String> processed = new HashSet<>();
                    for (List<ResolvedDependency> deps : maven.getDependencies().values()) {
                        for (ResolvedDependency dep : deps) {
                            if (dep.isDirect() &&
                                    StringUtils.matchesGlob(dep.getGroupId(), groupId) &&
                                    StringUtils.matchesGlob(dep.getArtifactId(), artifactId) &&
                                    processed.add(dep.getGroupId() + ":" + dep.getArtifactId())) {
                                // This is a matching parent dependency, resolve its transitives independently
                                Scope depScope = Scope.fromName(dep.getRequested().getScope());
                                Set<TransitiveDependency> transitives = acc.transitivesByProjectAndScope
                                        .computeIfAbsent(projectId, k -> new HashMap<>())
                                        .computeIfAbsent(depScope.name().toLowerCase(), k -> new HashSet<>());
                                resolveTransitivesFromPom(
                                        dep.getGav(),
                                        dep.getEffectiveExclusions(),
                                        maven.getPom().getRepositories(),
                                        downloader,
                                        ctx,
                                        transitives);
                            }
                        }
                    }
                });

                return tree;
            }

            private void resolveTransitivesFromPom(
                    ResolvedGroupArtifactVersion gav,
                    List<GroupArtifact> effectiveExclusions,
                    List<MavenRepository> repositories,
                    MavenPomDownloader downloader,
                    ExecutionContext ctx,
                    Set<TransitiveDependency> transitives) {
                try {
                    // Ensure we have Maven Central in the repositories
                    List<MavenRepository> effectiveRepos = new ArrayList<>(repositories);
                    if (effectiveRepos.stream().noneMatch(r -> r.getUri().contains("repo.maven.apache.org") ||
                            r.getUri().contains("repo1.maven.org"))) {
                        effectiveRepos.add(MavenRepository.MAVEN_CENTRAL);
                    }

                    // Get the resolved dependencies for compile scope (which includes most transitives)
                    Pom pom = downloader.download(gav.asGroupArtifactVersion(), null, null, effectiveRepos);
                    ResolvedPom resolvedPom = pom.resolve(emptyList(), downloader, effectiveRepos, ctx);
                    ResolvedPom patchedPom = applyExclusions(resolvedPom, effectiveExclusions);
                    List<ResolvedDependency> resolved = patchedPom.resolveDependencies(Scope.Compile, downloader, ctx);

                    // Collect all dependencies (both direct and transitive of the parent)
                    Set<ResolvedGroupArtifactVersion> visited = new HashSet<>();
                    for (ResolvedDependency dep : resolved) {
                        collectAllDependencies(dep, transitives, visited);
                    }
                } catch (MavenDownloadingException | MavenDownloadingExceptions e) {
                    // If we can't download/resolve the POM, fall back to not detecting redundancies
                    // This is a best-effort approach
                }
            }

            private ResolvedPom applyExclusions(ResolvedPom resolvedPom, List<GroupArtifact> effectiveExclusions) {
                ResolvedPom patchedPom = resolvedPom.withRequested(resolvedPom.getRequested().withDependencies(
                        ListUtils.filter(resolvedPom.getRequested().getDependencies(), d -> effectiveExclusions.stream()
                                .noneMatch(e -> e.getGroupId().equals(d.getGroupId()) && e.getArtifactId().equals(d.getArtifactId())))));
                patchedPom.getRequestedDependencies().removeIf(d -> effectiveExclusions.stream()
                        .anyMatch(e -> e.getGroupId().equals(d.getGroupId()) && e.getArtifactId().equals(d.getArtifactId())));
                return patchedPom;
            }

            private void collectAllDependencies(ResolvedDependency dep, Set<TransitiveDependency> transitives,
                                                Set<ResolvedGroupArtifactVersion> visited) {
                if (visited.add(dep.getGav())) {
                    transitives.add(new TransitiveDependency(dep.getGav(), new HashSet<>(dep.getEffectiveExclusions())));
                    for (ResolvedDependency transitive : dep.getDependencies()) {
                        collectAllDependencies(transitive, transitives, visited);
                    }
                }
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                Optional<GradleProject> gradleOpt = tree.getMarkers().findFirst(GradleProject.class);
                if (gradleOpt.isPresent()) {
                    GradleProject gradle = gradleOpt.get();
                    return handleGradle(ctx, gradle, tree);
                }

                Optional<MavenResolutionResult> mavenOpt = tree.getMarkers().findFirst(MavenResolutionResult.class);
                if (mavenOpt.isPresent()) {
                    MavenResolutionResult maven = mavenOpt.get();
                    return handleMaven(ctx, maven, tree);
                }

                return tree;
            }

            private @Nullable Tree handleGradle(ExecutionContext ctx, GradleProject gradle, Tree result) {
                String projectId = gradle.getGroup() + ":" + gradle.getName();
                Map<String, Set<TransitiveDependency>> scopeToTransitives =
                        acc.transitivesByProjectAndScope.getOrDefault(projectId, emptyMap());

                for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                    Set<TransitiveDependency> transitives = getCompatibleTransitives(
                            scopeToTransitives, conf.getName());
                    if (transitives.isEmpty()) {
                        continue;
                    }

                    for (ResolvedDependency dep : conf.getResolved()) {
                        if (dep.isDirect() &&
                                doesNotMatchArguments(dep) &&
                                isRedundant(dep, transitives)) {
                            // This direct dependency is transitively provided, remove it
                            // Don't specify configuration - Gradle's resolved config names differ from declaration names
                            result = new RemoveDependency(
                                    dep.getGroupId(), dep.getArtifactId(), null, null, null)
                                    .getVisitor().visit(result, ctx);
                        }
                    }
                }
                return result;
            }

            private @Nullable Tree handleMaven(ExecutionContext ctx, MavenResolutionResult maven, Tree result) {
                String projectId = maven.getPom().getGroupId() + ":" + maven.getPom().getArtifactId();
                Map<String, Set<TransitiveDependency>> scopeToTransitives =
                        acc.transitivesByProjectAndScope.getOrDefault(projectId, emptyMap());

                // A direct dependency appears under every scope bucket it is visible in; evaluate each
                // one once using its own effective scope so a wider transitive scope does not falsely
                // mark a narrower direct declaration as redundant.
                Set<String> processed = new HashSet<>();
                for (List<ResolvedDependency> deps : maven.getDependencies().values()) {
                    for (ResolvedDependency dep : deps) {
                        if (dep.isDirect() &&
                                doesNotMatchArguments(dep) &&
                                processed.add(dep.getGroupId() + ":" + dep.getArtifactId())) {
                            Scope depScope = Scope.fromName(dep.getRequested().getScope());
                            Set<TransitiveDependency> transitives = scopeToTransitives.getOrDefault(
                                    depScope.name().toLowerCase(), emptySet());
                            if (isRedundant(dep, transitives)) {
                                // This direct dependency is transitively provided at the same scope and
                                // with the same exclusions, remove it.
                                result = new RemoveDependency(
                                        dep.getGroupId(), dep.getArtifactId(), null, null, depScope.name().toLowerCase())
                                        .getVisitor().visit(result, ctx);
                            }
                        }
                    }
                }
                return result;
            }

            private boolean doesNotMatchArguments(ResolvedDependency dep) {
                return !StringUtils.matchesGlob(dep.getGroupId(), groupId) ||
                        !StringUtils.matchesGlob(dep.getArtifactId(), artifactId);
            }

            private boolean isRedundant(ResolvedDependency dep, Set<TransitiveDependency> transitives) {
                Set<GroupArtifact> depExclusions = new HashSet<>(dep.getEffectiveExclusions());
                for (TransitiveDependency transitive : transitives) {
                    ResolvedGroupArtifactVersion gav = transitive.getGav();
                    if (dep.getGroupId().equals(gav.getGroupId()) &&
                            dep.getArtifactId().equals(gav.getArtifactId()) &&
                            dep.getVersion().equals(gav.getVersion()) &&
                            depExclusions.equals(transitive.getExclusions())) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * Get Gradle transitives from this configuration and any broader ones.
             */
            private Set<TransitiveDependency> getCompatibleTransitives(
                    Map<String, Set<TransitiveDependency>> scopeToTransitives,
                    String targetScope) {

                Set<TransitiveDependency> result = new HashSet<>();

                // Always include transitives from the same scope
                Set<TransitiveDependency> sameScope = scopeToTransitives.get(targetScope);
                if (sameScope != null) {
                    result.addAll(sameScope);
                }

                // Include transitives from broader scopes
                for (String broader : getBroaderGradleScopes(targetScope)) {
                    Set<TransitiveDependency> broaderTransitives = scopeToTransitives.get(broader);
                    if (broaderTransitives != null) {
                        result.addAll(broaderTransitives);
                    }
                }

                return result;
            }

            private List<String> getBroaderGradleScopes(String scope) {
                switch (scope.toLowerCase()) {
                    case "runtimeonly":
                    case "runtimeclasspath":
                        return Arrays.asList("implementation", "api");
                    case "implementation":
                        return singletonList("api");
                    case "testimplementation":
                    case "testruntimeonly":
                        return Arrays.asList("implementation", "api", "testImplementation");
                    default:
                        return emptyList();
                }
            }
        };
    }
}
