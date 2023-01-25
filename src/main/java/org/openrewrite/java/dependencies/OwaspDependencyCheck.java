/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Validated;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.dependencies.table.OwaspVulnerabilityReport;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Xml;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.AbstractAnalyzer;
import org.owasp.dependencycheck.analyzer.Analyzer;
import org.owasp.dependencycheck.analyzer.CPEAnalyzer;
import org.owasp.dependencycheck.analyzer.NvdCveAnalyzer;
import org.owasp.dependencycheck.data.nexus.MavenArtifact;
import org.owasp.dependencycheck.data.update.exception.UpdateException;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Vulnerability;
import org.owasp.dependencycheck.dependency.naming.PurlIdentifier;
import org.owasp.dependencycheck.exception.ExceptionCollection;
import org.owasp.dependencycheck.utils.Settings;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.owasp.dependencycheck.utils.Settings.KEYS.AUTO_UPDATE;

public class OwaspDependencyCheck extends Recipe {
    transient OwaspVulnerabilityReport report = new OwaspVulnerabilityReport(this);
    transient boolean updated;

    @Override
    public String getDisplayName() {
        return "Perform OWASP dependency check";
    }

    @Override
    public String getDescription() {
        return "Dependency-Check is a Software Composition Analysis (SCA) tool that attempts to detect publicly " +
               "disclosed vulnerabilities contained within a project’s dependencies. It does this by determining " +
               "if there is a Common Platform Enumeration (CPE) identifier for a given dependency. " +
               "If found, it will generate a report linking to the associated CVE entries. " +
               "See the [project's website](https://owasp.org/www-project-dependency-check/) for more details.";
    }

    @Override
    public Validated validate() {
        try (Engine ignored = getEngine()) {
            updated = true;
            return super.validate();
        } catch (UpdateException e) {
            return Validated.invalid("engine", null, "Must be able to initialize OWASP engine.", e);
        }
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        try (Engine engine = getEngine()) {
            return ListUtils.map(before, sourceFile -> {
                scanMaven(engine).visitNonNull(sourceFile, ctx);
                scanGradleGroovy(engine).visitNonNull(sourceFile, ctx);

                try {
                    engine.analyzeDependencies();
                } catch (ExceptionCollection e) {
                    throw new RuntimeException(e);
                }

                Map<GroupArtifactVersion, Set<Vulnerability>> vulnerabilities = new HashMap<>();
                for (Dependency dependency : engine.getDependencies()) {
                    if (!dependency.getVulnerabilities().isEmpty()) {
                        PurlIdentifier id = (PurlIdentifier) dependency.getSoftwareIdentifiers().iterator().next();
                        GroupArtifactVersion gav = new GroupArtifactVersion(id.getNamespace(), id.getName(),
                                id.getVersion());
                        for (Vulnerability vulnerability : dependency.getVulnerabilities()) {
                            vulnerabilities.computeIfAbsent(gav, k -> new HashSet<>()).add(vulnerability);
                        }
                    }
                }

                SourceFile s = sourceFile;
                s = (SourceFile) reportMaven(vulnerabilities).visitNonNull(s, ctx);
                s = (SourceFile) reportGradleGroovy(vulnerabilities).visitNonNull(s, ctx);
                return s;
            });
        } catch (UpdateException e) {
            throw new RuntimeException(e);
        }
    }

    private MavenVisitor<ExecutionContext> scanMaven(Engine engine) {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                for (Map.Entry<Scope, List<ResolvedDependency>> scopeDependencies : getResolutionResult().getDependencies().entrySet()) {
                    for (ResolvedDependency resolvedDependency : scopeDependencies.getValue()) {
                        analyzeDependency(engine, resolvedDependency);
                    }
                }
                engine.getDependencies();
                return super.visitDocument(document, ctx);
            }
        };
    }

    private GroovyVisitor<ExecutionContext> scanGradleGroovy(Engine engine) {
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public G.CompilationUnit visitCompilationUnit(G.CompilationUnit cu, ExecutionContext ctx) {
                cu.getMarkers().findFirst(GradleProject.class).ifPresent(gradleProject -> {
                    for (GradleDependencyConfiguration configuration : gradleProject.getConfigurations()) {
                        for (ResolvedDependency resolvedDependency : configuration.getResolved()) {
                            analyzeDependency(engine, resolvedDependency);
                        }
                    }
                });
                return super.visitCompilationUnit(cu, ctx);
            }
        };
    }

    private MavenVisitor<ExecutionContext> reportMaven(Map<GroupArtifactVersion, Set<Vulnerability>> vulnerabilities) {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (isDependencyTag()) {
                    ResolvedDependency resolved = findDependency(tag, null);
                    if (resolved != null) {
                        for (Map.Entry<GroupArtifactVersion, Set<Vulnerability>> vulnerabilitiesByGav : vulnerabilities.entrySet()) {
                            GroupArtifactVersion gav = vulnerabilitiesByGav.getKey();
                            ResolvedDependency match = resolved.findDependency(requireNonNull(gav.getGroupId()), gav.getArtifactId());
                            if (match != null) {
                                boolean vulnerable = false;
                                for (Vulnerability vulnerability : vulnerabilitiesByGav.getValue()) {
                                    report.insertRow(ctx, new OwaspVulnerabilityReport.Row(
                                            gav.getGroupId(),
                                            gav.getArtifactId(),
                                            gav.getVersion(),
                                            vulnerability.getName(),
                                            vulnerability.getDescription(),
                                            vulnerability.getCvssV3().getBaseScore()
                                    ));
                                    vulnerable = true;
                                }
                                if (vulnerable) {
                                    return SearchResult.found(tag, "This dependency includes " + gav + " which has the following vulnerabilities:\n" +
                                                                   vulnerabilitiesByGav.getValue().stream()
                                                                           .map(v -> v.getName() + " (" + v.getCvssV3().getBaseScore() + ") - " + v.getDescription())
                                                                           .collect(Collectors.joining("\n")));
                                }
                            }
                        }
                    }
                }
                return super.visitTag(tag, ctx);
            }
        };
    }

    private void analyzeDependency(Engine engine, ResolvedDependency resolved) {
        Dependency dependency = new Dependency();
        MavenArtifact mavenArtifact = new MavenArtifact(resolved.getGroupId(), resolved.getArtifactId(),
                resolved.getVersion());
        dependency.addAsEvidence("resolved", mavenArtifact, Confidence.HIGHEST);
        engine.addDependency(dependency);
        for (ResolvedDependency transitive : resolved.getDependencies()) {
            analyzeDependency(engine, transitive);
        }
    }

    private GroovyVisitor<ExecutionContext> reportGradleGroovy(Map<GroupArtifactVersion, Set<Vulnerability>> vulnerabilities) {
        // TODO implement me!
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public G.CompilationUnit visitCompilationUnit(G.CompilationUnit cu, ExecutionContext ctx) {
                return super.visitCompilationUnit(cu, ctx);
            }
        };
    }

    Engine getEngine() throws UpdateException {
        Settings settings = new Settings();
        settings.setBooleanIfNotNull(AUTO_UPDATE, true);
        Engine engine = new Engine(settings);

        for (Analyzer analyzer : engine.getAnalyzers()) {
            if (analyzer instanceof NvdCveAnalyzer || analyzer instanceof CPEAnalyzer) {
                continue;
            }
            ((AbstractAnalyzer) analyzer).setEnabled(false);
        }

        if (!updated) {
            engine.doUpdates();
        }
        return engine;
    }
}
