/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.table.RepositoryAccessibilityReport;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.util.*;

import static java.util.Collections.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class DependencyResolutionDiagnostic extends ScanningRecipe<DependencyResolutionDiagnostic.Accumulator> {

    transient RepositoryAccessibilityReport report = new RepositoryAccessibilityReport(this);

    @Override
    public String getDisplayName() {
        return "Dependency resolution diagnostic";
    }

    @Override
    public String getDescription() {
        return "Recipes which manipulate dependencies must be able to successfully access the repositories used by the " +
               "project and retrieve dependency metadata from them. This recipe lists the repositories that were found " +
               "and whether or not dependency metadata could successfully be resolved from them.";
    }

    public static class Accumulator {
        Set<MavenRepository> repositoriesFromGradle = new HashSet<>();
        Set<MavenRepository> repositoriesFromMaven = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if(tree == null) {
                    return null;
                }
                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
                    acc.repositoriesFromGradle.addAll(gp.getMavenRepositories());
                    acc.repositoriesFromGradle.addAll(gp.getMavenPluginRepositories());
                });
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr ->
                    acc.repositoriesFromMaven.addAll(mrr.getPom().getRepositories()));
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Set<String> succeeded = new HashSet<>();
        Map<String, String> failed = new HashMap<>();
        record(true, acc.repositoriesFromMaven, succeeded, failed, ctx);
        record(false, acc.repositoriesFromGradle, succeeded, failed, ctx);
        for(String uri : succeeded) {
            report.insertRow(ctx, new RepositoryAccessibilityReport.Row(uri, ""));
        }
        for (Map.Entry<String, String> uriToFailure : failed.entrySet()) {
            report.insertRow(ctx, new RepositoryAccessibilityReport.Row(uriToFailure.getKey(), uriToFailure.getValue()));
        }

        return emptyList();
    }

    private static void record(boolean addMavenDefaultRepositories, Collection<MavenRepository> repos, Set<String> succeeded, Map<String, String> failed, ExecutionContext ctx) {
        // Use MavenPomDownloader without any default repositories, so we can test exactly one repository at a time
        MavenPomDownloader mpd = new MavenPomDownloader(ctx);
        Collection<MavenRepository> effectiveRepos = repos;
        if(addMavenDefaultRepositories) {
            if(!effectiveRepos.contains(MavenRepository.MAVEN_LOCAL_DEFAULT)) {
                effectiveRepos = new ArrayList<>(effectiveRepos);
                effectiveRepos.add(MavenRepository.MAVEN_LOCAL_DEFAULT);
            }
            if(!effectiveRepos.contains(MavenRepository.MAVEN_CENTRAL)) {
                effectiveRepos = new ArrayList<>(effectiveRepos);
                effectiveRepos.add(MavenRepository.MAVEN_CENTRAL);
            }
        }

        // Some repositories don't respond to a simple ping, so try requesting a dependency
        // Since there's no dependency that every repository can be guaranteed to have, try to download one that
        // doesn't exist and interpret non-404 results as failure
        GroupArtifactVersion gav = new GroupArtifactVersion("org.openrewrite.nonexistent", "nonexistent", "0.0.0");

        for (MavenRepository repo : effectiveRepos) {
            String uri = noTrailingSlash(repo.getUri());
            if(succeeded.contains(uri) || failed.containsKey(uri)) {
                continue;
            }
            if(uri.startsWith("file:/")) {
                // Local repositories are always accessible
                succeeded.add(uri);
                continue;
            }
            try {
                mpd.download(gav, null, null, Collections.singletonList(repo));
            } catch (MavenDownloadingException e) {
                if(e.getRepositoryResponses().isEmpty()) {
                    record(repo, e.getMessage(), succeeded, failed);
                } else {
                    for (Map.Entry<MavenRepository, String> result : e.getRepositoryResponses().entrySet()) {
                        record(result.getKey(), result.getValue(), succeeded, failed);
                    }
                }
            }
        }
    }

    private static void record(MavenRepository repo, String message, Set<String> succeeded, Map<String, String> failed) {
        if (message.contains("404") ||
            "Did not attempt to download because of a previous failure to retrieve from this repository.".equals(message) ||
            "Local repository does not contain pom".equals(message)) {
            succeeded.add(noTrailingSlash(repo.getUri()));
        } else {
            if("org.openrewrite.nonexistent:nonexistent:0.0.0 failed. Unable to download POM.".equals(message)) {
                failed.put(noTrailingSlash(repo.getUri()), "No response from repository");
            } else {
                failed.put(noTrailingSlash(repo.getUri()), message);
            }
        }
    }

    private static String noTrailingSlash(String uri) {
        if(uri.endsWith("/")) {
            return uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public G.CompilationUnit visitCompilationUnit(G.CompilationUnit cu, ExecutionContext ctx) {
                Optional<GradleProject> maybeGp = cu.getMarkers().findFirst(GradleProject.class);
                if (!maybeGp.isPresent()) {
                    return cu;
                }
                GradleProject gp = maybeGp.get();
                G.CompilationUnit g = super.visitCompilationUnit(cu, ctx);

                for (GradleDependencyConfiguration conf : gp.getConfigurations()) {
                    //noinspection ConstantValue
                    if (conf.getExceptionType() == null) {
                        continue;
                    }
                    g = SearchResult.found(g, "Found Gradle dependency configuration which failed to resolve during parsing: " +
                                              conf.getName() + ": " + conf.getExceptionType() + " - " + conf.getMessage());
                    // If one configuration failed to resolve, others likely failed and probably for the same reasons
                    // Record only first failure to reduce noise
                    break;
                }

                return g;
            }
        };
    }
}
