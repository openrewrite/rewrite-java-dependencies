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
import org.openrewrite.marker.Markup;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenRepositoryMirror;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.*;
import static org.openrewrite.internal.StringUtils.isBlank;

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
               "The Repository accessibility report lists all the artifact repositories known to the project and whether " +
               "respond to network access. The network access is attempted while the recipe is run and so is " +
               "representative of current conditions. \n\n" +
               "The Gradle dependency configuration errors lists all the dependency configurations that failed to " +
               "resolve one or more dependencies when the project was parsed. This is representative of conditions at " +
               "the time the LST was parsed.";
    }


    @Option(displayName = "Group ID",
            description = "The group ID of a dependency to attempt to download from the repository. " +
                          "Default value is \"com.fasterxml.jackson.core\". " +
                          "If this dependency is not found in the repository the error will be noted in the report. " +
                          "There is no need to specify an alternate value for this parameter unless the repository is known not to contain jackson-core.",
            example = "com.fasterxml.jackson.core",
            required = false)
    @Nullable
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The artifact ID of a dependency to attempt to download from the repository. " +
                          "Default value is \"jackson-core\". " +
                          "If this dependency is not found in the repository the error will be noted in the report. " +
                          "There is no need to specify an alternate value for this parameter unless the repository is known not to contain jackson-core.",
            example = "jackson-core",
            required = false)
    @Nullable
    String artifactId;

    @Option(displayName = "Version",
            description = "The version of a dependency to attempt to download from the repository. " +
                          "Default value is \"2.16.0\". " +
                          "If this dependency is not found in the repository the error will be noted in the report. " +
                          "There is no need to specify an alternate value for this parameter unless the repository is known not to contain jackson-core.",
            example = "2.16.0",
            required = false)
    @Nullable
    String version;

    public static class Accumulator {
        boolean foundGradle;
        Set<MavenRepository> repositoriesFromGradle = new HashSet<>();

        boolean foundMaven;
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
                if(!(tree instanceof SourceFile)) {
                    return null;
                }
                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
                    acc.foundGradle = true;
                    acc.repositoriesFromGradle.addAll(gp.getMavenRepositories());
                    acc.repositoriesFromGradle.addAll(gp.getMavenPluginRepositories());
                });
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                    acc.foundMaven = true;
                    acc.repositoriesFromMaven.addAll(mrr.getPom().getRepositories());
                });
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Set<String> seen = new HashSet<>();
        if(acc.foundMaven) {
            record(true, acc.repositoriesFromMaven, seen, ctx);
        }
        if(acc.foundGradle) {
            record(false, acc.repositoriesFromGradle, seen, ctx);
        }
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

                MavenExecutionContextView mctx = new MavenExecutionContextView(ctx);
                MavenSettings settings = mctx.getSettings();
                if(settings != null) {
                    // normalizeRepository() internally applies mirrors,but normalizeRepository() just returned null.
                    // Replicate mirror application so that the correct URL is recorded
                    repo = MavenRepositoryMirror.apply(mctx.getMirrors(settings), repo);
                }
                if(seen.add(noTrailingSlash(repo.getUri()))) {
                    report.insertRow(ctx, rowFor(repo, reason, null));
                }
            } else {
                if(seen.add(noTrailingSlash(normalized.getUri()))) {
                    GroupArtifactVersion gav = new GroupArtifactVersion(
                            isBlank(groupId) ? "com.fasterxml.jackson.core" : groupId,
                            isBlank(artifactId) ? "jackson-core" : artifactId,
                            isBlank(version) ? "2.16.0" : version);
                    Throwable resolutionThrowable = null;
                    try {
                        mpd.download(gav, null, null, singletonList(normalized));
                    } catch (Exception e) {
                        resolutionThrowable = e;
                    }
                    report.insertRow(ctx, rowFor(normalized, null, resolutionThrowable));
                }
            }
        }
    }

    private static String noTrailingSlash(String uri) {
        if(uri.endsWith("/")) {
            return uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private static RepositoryAccessibilityReport.Row rowFor(MavenRepository repo, @Nullable Throwable pingThrowable, @Nullable Throwable resolveThrowable) {
        Integer pingHttpResponseCode = null;
        String pingExceptionClass = "";
        String pingExceptionMessage = "";
        if(pingThrowable instanceof MavenPomDownloader.HttpSenderResponseException) {
            pingHttpResponseCode = ((MavenPomDownloader.HttpSenderResponseException) pingThrowable).getResponseCode();
            pingThrowable = pingThrowable.getCause();
        }
        if(pingThrowable instanceof UncheckedIOException) {
            pingThrowable = pingThrowable.getCause();
        }
        if(pingThrowable == null) {
            pingHttpResponseCode = 200;
        } else {
            pingExceptionClass = pingThrowable.getClass().getName();
            pingExceptionMessage = pingThrowable.getMessage();
        }
        String resolveExceptionClass = "";
        String resolveExceptionMessage = "";
        if(resolveThrowable instanceof MavenPomDownloader.HttpSenderResponseException) {
            resolveThrowable = resolveThrowable.getCause();
        }
        if(resolveThrowable instanceof UncheckedIOException) {
            resolveThrowable = resolveThrowable.getCause();
        }
        if(resolveThrowable != null) {
            resolveExceptionClass = resolveThrowable.getClass().getName();
            resolveExceptionMessage = resolveThrowable.getMessage();
        }
        return new RepositoryAccessibilityReport.Row(noTrailingSlash(repo.getUri()), pingExceptionClass, pingExceptionMessage, pingHttpResponseCode,
                resolveExceptionClass, resolveExceptionMessage);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {

        GroovyIsoVisitor<ExecutionContext> gv = new GroovyIsoVisitor<ExecutionContext>() {
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

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if(!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if(s.getSourcePath().endsWith("build.gradle") && !s.getMarkers().findFirst(GradleProject.class).isPresent()) {
                    if(s.getMarkers().getMarkers().stream().anyMatch(marker -> marker.getClass().getName().equals("org.openrewrite.gradle.marker.GradleProject"))) {
                        s = Markup.error(s, new IllegalStateException(
                                s.getSourcePath() + " has a GradleProject marker, but it is loaded by a different classloader than the recipe."));
                    } else {
                        s = Markup.warn(s, new IllegalStateException(
                                s.getSourcePath() + " is a Gradle build file, but it is missing a GradleProject marker."));
                    }
                } else if(s.getSourcePath().endsWith("pom.xml") && !s.getMarkers().findFirst(MavenResolutionResult.class).isPresent()) {
                    if(s.getMarkers().getMarkers().stream().anyMatch(marker -> marker.getClass().getName().equals("org.openrewrite.maven.tree.MavenResolutionResult"))) {
                        s = Markup.error(s, new IllegalStateException(
                                s.getSourcePath() + " has a MavenResolutionResult marker, but it is loaded by a different classloader than the recipe."));
                    } else {
                        s = Markup.warn(s, new IllegalStateException(
                                s.getSourcePath() + " is a Maven pom, but it is missing a MavenResolutionResult marker."));
                    }
                }
                if(gv.isAcceptable(s, ctx)) {
                    s = (SourceFile) gv.visit(s, ctx);
                }
                return s;
            }
        };
    }
}
