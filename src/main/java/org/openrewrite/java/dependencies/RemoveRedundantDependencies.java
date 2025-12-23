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
package org.openrewrite.java.dependencies;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.MavenDownloadingExceptions;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.*;

import java.util.*;

import static java.util.Collections.emptyList;

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

    @Option(displayName = "Scope",
            description = "Only remove redundant dependencies from the specified Maven scope. If not specified, all scopes are considered.",
            valid = {"compile", "runtime", "provided", "test"},
            example = "compile",
            required = false)
    @Nullable
    String scope;

    @Option(displayName = "Configuration",
            description = "Only remove redundant dependencies from the specified Gradle configuration. If not specified, all configurations are considered.",
            example = "implementation",
            required = false)
    @Nullable
    String configuration;

    @Override
    public String getDisplayName() {
        return "Remove redundant explicit dependencies";
    }

    @Override
    public String getDescription() {
        return "Remove explicit dependencies that are already provided transitively by a specified dependency. " +
               "This recipe downloads and resolves the parent dependency's POM to determine its true transitive " +
               "dependencies, allowing it to detect redundancies even when both dependencies are explicitly declared.";
    }

    @Value
    public static class Accumulator {
        // Map from project identifier -> scope/configuration -> Set of transitive GAVs
        Map<String, Map<String, Set<ResolvedGroupArtifactVersion>>> transitivesByProjectAndScope;
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

                    // For Gradle, store all transitives in a single set per project
                    // because Gradle's configuration hierarchy is complex
                    Set<ResolvedGroupArtifactVersion> projectTransitives = acc.transitivesByProjectAndScope
                            .computeIfAbsent(projectId, k -> new HashMap<>())
                            .computeIfAbsent("all", k -> new HashSet<>());

                    for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                        if (configuration != null && !configuration.equals(conf.getName())) {
                            continue;
                        }
                        for (ResolvedDependency dep : conf.getResolved()) {
                            if (dep.getDepth() == 0 &&
                                StringUtils.matchesGlob(dep.getGroupId(), groupId) &&
                                StringUtils.matchesGlob(dep.getArtifactId(), artifactId)) {
                                // This is a matching parent dependency, resolve its transitives independently
                                resolveTransitivesFromPom(dep.getGav(), gradle.getMavenRepositories(),
                                        downloader, ctx, projectTransitives);
                            }
                        }
                    }
                });

                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(maven -> {
                    String projectId = maven.getPom().getGroupId() + ":" + maven.getPom().getArtifactId();
                    MavenPomDownloader downloader = new MavenPomDownloader(ctx);
                    List<MavenRepository> repositories = maven.getPom().getRepositories();

                    for (Map.Entry<Scope, List<ResolvedDependency>> entry : maven.getDependencies().entrySet()) {
                        Scope depScope = entry.getKey();
                        if (scope != null && !scope.equalsIgnoreCase(depScope.name())) {
                            continue;
                        }
                        for (ResolvedDependency dep : entry.getValue()) {
                            if (dep.getDepth() == 0 &&
                                StringUtils.matchesGlob(dep.getGroupId(), groupId) &&
                                StringUtils.matchesGlob(dep.getArtifactId(), artifactId)) {
                                // This is a matching parent dependency, resolve its transitives independently
                                Set<ResolvedGroupArtifactVersion> transitives = acc.transitivesByProjectAndScope
                                        .computeIfAbsent(projectId, k -> new HashMap<>())
                                        .computeIfAbsent(depScope.name().toLowerCase(), k -> new HashSet<>());
                                resolveTransitivesFromPom(dep.getGav(), repositories, downloader, ctx, transitives);
                            }
                        }
                    }
                });

                return tree;
            }

            private void resolveTransitivesFromPom(ResolvedGroupArtifactVersion gav,
                                                   List<MavenRepository> repositories,
                                                   MavenPomDownloader downloader,
                                                   ExecutionContext ctx,
                                                   Set<ResolvedGroupArtifactVersion> transitives) {
                try {
                    // Download the parent dependency's POM
                    GroupArtifactVersion parentGav = new GroupArtifactVersion(
                            gav.getGroupId(), gav.getArtifactId(), gav.getVersion());

                    // Ensure we have Maven Central in the repositories
                    List<MavenRepository> effectiveRepos = new ArrayList<>(repositories);
                    if (effectiveRepos.stream().noneMatch(r -> r.getUri().contains("repo.maven.apache.org") ||
                                                                r.getUri().contains("repo1.maven.org"))) {
                        effectiveRepos.add(MavenRepository.MAVEN_CENTRAL);
                    }

                    Pom pom = downloader.download(parentGav, null, null, effectiveRepos);

                    // Resolve the POM to get its full dependency tree
                    ResolvedPom resolvedPom = pom.resolve(emptyList(), downloader, effectiveRepos, ctx);

                    // Get the resolved dependencies for compile scope (which includes most transitives)
                    List<ResolvedDependency> resolved = resolvedPom.resolveDependencies(Scope.Compile, downloader, ctx);

                    // Collect all dependencies (both direct and transitive of the parent)
                    for (ResolvedDependency dep : resolved) {
                        collectAllDependencies(dep, transitives);
                    }
                } catch (MavenDownloadingException | MavenDownloadingExceptions e) {
                    // If we can't download/resolve the POM, fall back to not detecting redundancies
                    // This is a best-effort approach
                }
            }

            private void collectAllDependencies(ResolvedDependency dep, Set<ResolvedGroupArtifactVersion> transitives) {
                if (transitives.add(dep.getGav())) {
                    for (ResolvedDependency transitive : dep.getDependencies()) {
                        collectAllDependencies(transitive, transitives);
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

                SourceFile sf = (SourceFile) tree;
                Tree result = sf;

                // Handle Gradle
                Optional<GradleProject> gradleOpt = sf.getMarkers().findFirst(GradleProject.class);
                if (gradleOpt.isPresent()) {
                    GradleProject gradle = gradleOpt.get();
                    String projectId = gradle.getGroup() + ":" + gradle.getName();
                    Map<String, Set<ResolvedGroupArtifactVersion>> scopeToTransitives =
                            acc.transitivesByProjectAndScope.getOrDefault(projectId, Collections.emptyMap());

                    // For Gradle, use the "all" bucket we created in scanner
                    Set<ResolvedGroupArtifactVersion> transitives = scopeToTransitives.getOrDefault("all", Collections.emptySet());
                    if (transitives.isEmpty()) {
                        return result;
                    }

                    for (GradleDependencyConfiguration conf : gradle.getConfigurations()) {
                        if (configuration != null && !configuration.equals(conf.getName())) {
                            continue;
                        }

                        for (ResolvedDependency dep : conf.getResolved()) {
                            if (dep.getDepth() == 0 &&
                                isRedundantDependency(dep) &&
                                isInTransitives(dep, transitives)) {
                                // This direct dependency is transitively provided, remove it
                                // Don't specify configuration - Gradle's resolved config names differ from declaration names
                                TreeVisitor<?, ExecutionContext> removeDep =
                                        new org.openrewrite.gradle.RemoveDependency(
                                                dep.getGroupId(), dep.getArtifactId(), configuration
                                        ).getVisitor();
                                result = removeDep.visit(result, ctx);
                            }
                        }
                    }
                    return result;
                }

                // Handle Maven
                Optional<MavenResolutionResult> mavenOpt = sf.getMarkers().findFirst(MavenResolutionResult.class);
                if (mavenOpt.isPresent()) {
                    MavenResolutionResult maven = mavenOpt.get();
                    String projectId = maven.getPom().getGroupId() + ":" + maven.getPom().getArtifactId();
                    Map<String, Set<ResolvedGroupArtifactVersion>> scopeToTransitives =
                            acc.transitivesByProjectAndScope.getOrDefault(projectId, Collections.emptyMap());

                    for (Map.Entry<Scope, List<ResolvedDependency>> entry : maven.getDependencies().entrySet()) {
                        Scope depScope = entry.getKey();
                        if (scope != null && !scope.equalsIgnoreCase(depScope.name())) {
                            continue;
                        }
                        Set<ResolvedGroupArtifactVersion> transitives = getCompatibleTransitives(
                                scopeToTransitives, depScope.name().toLowerCase(), false);
                        if (transitives.isEmpty()) {
                            continue;
                        }

                        for (ResolvedDependency dep : entry.getValue()) {
                            if (dep.getDepth() == 0 &&
                                isRedundantDependency(dep) &&
                                isInTransitives(dep, transitives)) {
                                // This direct dependency is transitively provided, remove it
                                TreeVisitor<?, ExecutionContext> removeDep =
                                        new org.openrewrite.maven.RemoveDependency(
                                                dep.getGroupId(), dep.getArtifactId(), depScope.name().toLowerCase()
                                        ).getVisitor();
                                result = removeDep.visit(result, ctx);
                            }
                        }
                    }
                    return result;
                }

                return tree;
            }

            private boolean isRedundantDependency(ResolvedDependency dep) {
                return !StringUtils.matchesGlob(dep.getGroupId(), groupId) ||
                       !StringUtils.matchesGlob(dep.getArtifactId(), artifactId);
            }

            private boolean isInTransitives(ResolvedDependency dep, Set<ResolvedGroupArtifactVersion> transitives) {
                // Check if this dependency's GAV matches any transitive
                // We match on groupId:artifactId:version exactly
                for (ResolvedGroupArtifactVersion transitive : transitives) {
                    if (dep.getGroupId().equals(transitive.getGroupId()) &&
                        dep.getArtifactId().equals(transitive.getArtifactId()) &&
                        dep.getVersion().equals(transitive.getVersion())) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * Get transitives from this scope and any broader scopes.
             * For Maven: compile covers runtime; compile/runtime cover provided
             * For Gradle: api covers implementation; implementation covers runtimeOnly
             */
            private Set<ResolvedGroupArtifactVersion> getCompatibleTransitives(
                    Map<String, Set<ResolvedGroupArtifactVersion>> scopeToTransitives,
                    String targetScope, boolean isGradle) {

                Set<ResolvedGroupArtifactVersion> result = new HashSet<>();

                // Always include transitives from the same scope
                Set<ResolvedGroupArtifactVersion> sameScope = scopeToTransitives.get(targetScope);
                if (sameScope != null) {
                    result.addAll(sameScope);
                }

                // Include transitives from broader scopes
                List<String> broaderScopes = getBroaderScopes(targetScope, isGradle);
                for (String broader : broaderScopes) {
                    Set<ResolvedGroupArtifactVersion> broaderTransitives = scopeToTransitives.get(broader);
                    if (broaderTransitives != null) {
                        result.addAll(broaderTransitives);
                    }
                }

                return result;
            }

            private List<String> getBroaderScopes(String scope, boolean isGradle) {
                if (isGradle) {
                    switch (scope.toLowerCase()) {
                        case "runtimeonly":
                        case "runtimeclasspath":
                            return Arrays.asList("implementation", "api");
                        case "implementation":
                            return Collections.singletonList("api");
                        case "testimplementation":
                        case "testruntimeonly":
                            return Arrays.asList("implementation", "api", "testImplementation");
                        default:
                            return Collections.emptyList();
                    }
                } else {
                    // Maven scopes
                    switch (scope.toLowerCase()) {
                        case "runtime":
                            return Collections.singletonList("compile");
                        case "provided":
                            return Arrays.asList("compile", "runtime");
                        case "test":
                            return Arrays.asList("compile", "runtime", "provided");
                        default:
                            return Collections.emptyList();
                    }
                }
            }
        };
    }
}
