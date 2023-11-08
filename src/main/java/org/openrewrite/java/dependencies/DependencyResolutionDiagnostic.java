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
import org.openrewrite.java.dependencies.table.GradleDependencyConfigurationErrors;
import org.openrewrite.java.dependencies.table.RepositoryAccessibilityReport;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class DependencyResolutionDiagnostic extends ScanningRecipe<DependencyResolutionDiagnostic.Accumulator> {

    transient RepositoryAccessibilityReport report = new RepositoryAccessibilityReport(this);
    transient GradleDependencyConfigurationErrors gradleErrors = new GradleDependencyConfigurationErrors(this);

    @Override
    public String getDisplayName() {
        return "Dependency resolution diagnostic";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Recipes which manipulate dependencies must be able to successfully access the artifact repositories " +
               "and resolve dependencies from them. This recipe produces two data tables used to understand the state " +
               "of dependency resolution. \n\n" +
               "The Repository accessibility report lists all the artifact repositories known to the project and whether" +
               "they respond to network access. The network access is attempted while the recipe is run and so is " +
               "representative of current conditions. \n\n" +
               "The Gradle dependency configuration errors lists all the dependency configurations that failed to " +
               "resolve one or more dependencies when the project was parsed. This is representative of conditions at " +
               "the time the LST was parsed.";
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
        Set<String> seen = new HashSet<>();
        record(true, acc.repositoriesFromMaven, seen, ctx);
        record(false, acc.repositoriesFromGradle, seen, ctx);
        return emptyList();
    }

    private void record(boolean addMavenDefaultRepositories, Collection<MavenRepository> repos, Set<String> seen, ExecutionContext ctx) {
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
        for (MavenRepository repo : effectiveRepos) {
            if(seen.contains(noTrailingSlash(repo.getUri()))) {
                continue;
            }
            AtomicReference<Throwable> nullReason = new AtomicReference<>();
            MavenRepository normalized = mpd.normalizeRepository(repo, null, nullReason::set);
            if(normalized == null) {
                Throwable reason = nullReason.get();
                if(reason == null) {
                    reason = new RuntimeException("Repository unreachable for unknown reason");
                }
                seen.add(noTrailingSlash(repo.getUri()));
                report.insertRow(ctx, rowFor(repo, reason));
            } else {
                seen.add(noTrailingSlash(normalized.getUri()));
                report.insertRow(ctx, rowFor(repo, null));
            }
        }
    }

    private static String noTrailingSlash(String uri) {
        if(uri.endsWith("/")) {
            return uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private static RepositoryAccessibilityReport.Row rowFor(MavenRepository repo, @Nullable Throwable t) {
        Integer httpResponseCode = null;
        String exceptionClass = "";
        String exceptionMessage = "";
        if(t instanceof MavenPomDownloader.HttpSenderResponseException) {
            httpResponseCode = ((MavenPomDownloader.HttpSenderResponseException) t).getResponseCode();
            t = t.getCause();
        }
        if(t instanceof UncheckedIOException) {
            t = t.getCause();
        }
        if(t == null) {
            httpResponseCode = 200;
        } else {
            exceptionClass = t.getClass().getName();
            exceptionMessage = t.getMessage();
        }
        return new RepositoryAccessibilityReport.Row(repo.getUri(), exceptionClass, exceptionMessage, httpResponseCode);
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
                    gradleErrors.insertRow(ctx, new GradleDependencyConfigurationErrors.Row(gp.getPath(), conf.getName(), conf.getExceptionType(), conf.getMessage()));
                }

                return g;
            }
        };
    }
}
