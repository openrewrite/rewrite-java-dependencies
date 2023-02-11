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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.GroovyVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.table.LicenseReport;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.*;
import org.openrewrite.xml.tree.Xml;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class DependencyLicenseCheck extends Recipe {
    transient LicenseReport report = new LicenseReport(this);

    @Option(displayName = "Scope",
            description = "Match dependencies with the specified scope",
            valid = {"compile", "test", "runtime", "provided"},
            example = "compile")
    String scope;

    @Option(displayName = "Add markers",
            description = "Report each license transitively used by a dependency in search results.",
            required = false)
    @Nullable
    Boolean addMarkers;

    @Override
    public String getDisplayName() {
        return "Find licenses in use in third-party dependencies";
    }

    @Override
    public String getDescription() {
        return "Locates and reports on all licenses in use.";
    }

    @Override
    public Validated validate() {
        return super.validate().and(Validated.test("scope", "scope is a valid Maven scope", scope, s -> {
            try {
                Scope.fromName(s);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }));
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        Scope aScope = Scope.fromName(scope);
        Map<ResolvedGroupArtifactVersion, Set<License>> licenses = new HashMap<>();

        List<SourceFile> after = ListUtils.map(before, sourceFile -> {
            scanMaven(licenses, aScope).visitNonNull(sourceFile, ctx);
            scanGradleGroovy(licenses, aScope).visitNonNull(sourceFile, ctx);
            return sourceFile;
        });

        for (Map.Entry<ResolvedGroupArtifactVersion, Set<License>> licensesByGav : licenses.entrySet()) {
            ResolvedGroupArtifactVersion gav = licensesByGav.getKey();
            for (License license : licensesByGav.getValue()) {
                report.insertRow(ctx, new LicenseReport.Row(
                        gav.getGroupId(),
                        gav.getArtifactId(),
                        gav.getVersion(),
                        license.getName(),
                        license.getType().toString()
                ));
            }
        }

        return after;
    }

    private MavenVisitor<ExecutionContext> scanMaven(Map<ResolvedGroupArtifactVersion, Set<License>> licenses,
                                                     Scope aScope) {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                List<ResolvedDependency> scopeDependencies = getResolutionResult().getDependencies().get(aScope);
                if(scopeDependencies != null) {
                    for (ResolvedDependency resolvedDependency : scopeDependencies) {
                        analyzeDependency(resolvedDependency, licenses);
                    }
                }
                return super.visitDocument(document, ctx);
            }
        };
    }

    private GroovyVisitor<ExecutionContext> scanGradleGroovy(Map<ResolvedGroupArtifactVersion, Set<License>> licenses,
                                                             Scope aScope) {
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public G.CompilationUnit visitCompilationUnit(G.CompilationUnit cu, ExecutionContext ctx) {
                cu.getMarkers().findFirst(GradleProject.class).ifPresent(gradleProject -> {
                    for (GradleDependencyConfiguration configuration : gradleProject.getConfigurations()) {
                        // FIXME limit by scope
                        for (ResolvedDependency resolvedDependency : configuration.getResolved()) {
                            if (!StringUtils.isBlank(resolvedDependency.getVersion())) {
                                analyzeDependency(resolvedDependency, licenses);
                            }
                        }
                    }
                });
                return super.visitCompilationUnit(cu, ctx);
            }
        };
    }

    private void analyzeDependency(ResolvedDependency resolvedDependency, Map<ResolvedGroupArtifactVersion, Set<License>> licenses) {
        if (!resolvedDependency.getLicenses().isEmpty()) {
            licenses.computeIfAbsent(resolvedDependency.getGav(), gav -> new HashSet<>())
                    .addAll(resolvedDependency.getLicenses());
        } else {
            licenses.computeIfAbsent(resolvedDependency.getGav(), gav -> new HashSet<>())
                    .add(new License("", License.Type.Unknown));
        }
    }
}
