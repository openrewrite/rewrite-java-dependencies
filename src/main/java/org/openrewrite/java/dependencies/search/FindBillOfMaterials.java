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
package org.openrewrite.java.dependencies.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.xml.tree.Xml;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindBillOfMaterials extends Recipe {
    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Override
    public String getDisplayName() {
        return "Find Bill of Materials (BOM) dependencies";
    }

    @Override
    public String getDescription() {
        return "Find Bill of Materials (BOM) dependencies in Maven and Gradle build files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof Xml.Document) {
                    // Handle Maven projects
                    return new MavenBomVisitor().visitNonNull(tree, ctx);
                } else if (tree instanceof G.CompilationUnit || tree instanceof K.CompilationUnit) {
                    // Handle Gradle projects
                    return new GradleBomVisitor().visitNonNull(tree, ctx);
                }
                return tree;
            }
        };
    }

    private class MavenBomVisitor extends MavenIsoVisitor<ExecutionContext> {
        @Override
        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
            Xml.Tag t = super.visitTag(tag, ctx);

            // Check if we're in a dependency management dependency
            if ("dependency".equals(t.getName()) && isDependencyManagementDependency(getCursor())) {
                // Check if this dependency has type=pom and scope=import
                String type = null;
                String scope = null;
                String groupId = null;
                String artifactId = null;
                String version = null;

                for (Xml.Tag child : t.getChildren()) {
                    switch (child.getName()) {
                        case "type":
                            type = child.getValue().orElse(null);
                            break;
                        case "scope":
                            scope = child.getValue().orElse(null);
                            break;
                        case "groupId":
                            groupId = child.getValue().orElse(null);
                            break;
                        case "artifactId":
                            artifactId = child.getValue().orElse(null);
                            break;
                        case "version":
                            version = child.getValue().orElse(null);
                            break;
                    }
                }

                if ("pom".equals(type) && "import".equals(scope) && groupId != null && artifactId != null) {
                    // This is a BOM - mark it and record it
                    MavenResolutionResult mavenResult = getResolutionResult();
                    String projectName = mavenResult.getPom().getArtifactId();

                    // Resolve properties if they contain ${...}
                    groupId = resolveProperty(groupId, mavenResult);
                    artifactId = resolveProperty(artifactId, mavenResult);
                    version = resolveProperty(version, mavenResult);

                    dependenciesInUse.insertRow(ctx, new DependenciesInUse.Row(
                            projectName,
                            "main",
                            groupId,
                            artifactId,
                            version != null ? version : "unknown",
                            null,
                            "import",
                            0
                    ));

                    return SearchResult.found(t);
                }
            }

            return t;
        }

        private @Nullable String resolveProperty(@Nullable String value, MavenResolutionResult mavenResult) {
            if (value == null) {
                return value;
            }

            // Check if value contains a property reference ${...}
            if (value.startsWith("${") && value.endsWith("}")) {
                String propertyName = value.substring(2, value.length() - 1);
                // Try to resolve from properties
                String resolved = mavenResult.getPom().getProperties().get(propertyName);
                if (resolved != null) {
                    return resolved;
                }
            }

            return value;
        }

        private boolean isDependencyManagementDependency(Cursor cursor) {
            // Walk up the cursor path to see if we're inside a dependencyManagement section
            Cursor c = cursor;
            while (c != null) {
                Object value = c.getValue();
                if (value instanceof Xml.Tag) {
                    Xml.Tag tag = (Xml.Tag) value;
                    if ("dependencyManagement".equals(tag.getName())) {
                        return true;
                    }
                }
                c = c.getParent();
            }
            return false;
        }
    }

    private class GradleBomVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher platformMatcher = new MethodMatcher("*..* platform(..)");
        private final MethodMatcher enforcedPlatformMatcher = new MethodMatcher("*..* enforcedPlatform(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

            // Check if this is a platform or enforcedPlatform call
            if ((platformMatcher.matches(m) || enforcedPlatformMatcher.matches(m)) && !m.getArguments().isEmpty()) {
                // Extract the BOM coordinates from the argument
                Expression arg = m.getArguments().get(0);
                String gav = extractGav(arg);

                if (gav != null) {
                    String[] parts = gav.split(":");
                    if (parts.length >= 2) {
                        String groupId = parts[0];
                        String artifactId = parts[1];
                        String version = parts.length > 2 ? parts[2] : "unknown";

                        GradleProject gradleProject = getCursor().firstEnclosingOrThrow(SourceFile.class)
                                .getMarkers().findFirst(GradleProject.class).orElse(null);
                        String projectName = gradleProject != null ? gradleProject.getName() : "unknown";

                        dependenciesInUse.insertRow(ctx, new DependenciesInUse.Row(
                                projectName,
                                "main",
                                groupId,
                                artifactId,
                                version,
                                null,
                                "import",
                                0
                        ));

                        return SearchResult.found(m);
                    }
                }
            }

            // Also check for import configuration dependencies that might be BOMs
            if (m.getSimpleName().equals("implementation") || m.getSimpleName().equals("api") ||
                    m.getSimpleName().equals("compile") || m.getSimpleName().equals("runtime")) {

                // Check if the method call is wrapped in platform() or enforcedPlatform()
                if (m.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select = (J.MethodInvocation) m.getSelect();
                    if ("platform".equals(select.getSimpleName()) || "enforcedPlatform".equals(select.getSimpleName())) {
                        // This is a BOM dependency
                        if (!m.getArguments().isEmpty()) {
                            String gav = extractGav(m.getArguments().get(0));
                            if (gav != null) {
                                String[] parts = gav.split(":");
                                if (parts.length >= 2) {
                                    String groupId = parts[0];
                                    String artifactId = parts[1];
                                    String version = parts.length > 2 ? parts[2] : "unknown";

                                    GradleProject gradleProject = getCursor().firstEnclosingOrThrow(SourceFile.class)
                                            .getMarkers().findFirst(GradleProject.class).orElse(null);
                                    String projectName = gradleProject != null ? gradleProject.getName() : "unknown";

                                    dependenciesInUse.insertRow(ctx, new DependenciesInUse.Row(
                                            projectName,
                                            "main",
                                            groupId,
                                            artifactId,
                                            version,
                                            null,
                                            "import",
                                            0
                                    ));

                                    return SearchResult.found(m);
                                }
                            }
                        }
                    }
                }
            }

            return m;
        }

        private @Nullable String extractGav(Expression expr) {
            if (expr instanceof J.Literal) {
                Object value = ((J.Literal) expr).getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            } else if (expr instanceof G.GString) {
                // Handle GString interpolations for Gradle Kotlin/Groovy
                // This is a simplified extraction - in reality, we'd need to resolve variables
                return expr.toString();
            }
            return null;
        }
    }
}
