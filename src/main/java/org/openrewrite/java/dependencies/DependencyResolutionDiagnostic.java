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
        Set<MavenRepository> mavenRepositories = new HashSet<>();
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
                    acc.mavenRepositories.addAll(gp.getMavenRepositories());
                    acc.mavenRepositories.addAll(gp.getMavenPluginRepositories());
                });
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr ->
                        acc.mavenRepositories.addAll(mrr.getPom().getRepositories()));
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        MavenPomDownloader mpd = new MavenPomDownloader(ctx);
        // Since there's no dependency that every repository can be guaranteed to have, try to download one that
        // doesn't exist and interpret non-404 results as failure
        GroupArtifactVersion gav = new GroupArtifactVersion("org.openrewrite.nonexistent", "nonexistent", "0.0.0");
        Set<String> succeeded = new HashSet<>();
        Map<String, String> failed = new HashMap<>();
        for (MavenRepository repo : acc.mavenRepositories) {
            String uri = noTrailingSlash(repo.getUri());
            if(succeeded.contains(uri) || failed.containsKey(uri)) {
                continue;
            }
            try {
                mpd.download(gav, null, null, Collections.singletonList(repo));
            } catch (MavenDownloadingException e) {
                if(e.getRepositoryResponses().isEmpty()) {
                    failed.put(uri, "No response from repository");
                }
                for (Map.Entry<MavenRepository, String> result : e.getRepositoryResponses().entrySet()) {
                    if (result.getValue().contains("404") ||
                        "Did not attempt to download because of a previous failure to retrieve from this repository.".equals(result.getValue()) ||
                        "Local repository does not contain pom".equals(result.getValue())) {
                        succeeded.add(noTrailingSlash(result.getKey().getUri()));
                    } else {
                        failed.put(noTrailingSlash(result.getKey().getUri()), result.getValue());
                    }
                }
            }
        }
        for(String uri : succeeded) {
            report.insertRow(ctx, new RepositoryAccessibilityReport.Row(uri, ""));
        }
        for (Map.Entry<String, String> uriToFailure : failed.entrySet()) {
            report.insertRow(ctx, new RepositoryAccessibilityReport.Row(uriToFailure.getKey(), uriToFailure.getValue()));
        }

        return emptyList();
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
