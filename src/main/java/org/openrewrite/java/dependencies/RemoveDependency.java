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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.openrewrite.internal.StringUtils.matchesGlob;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveDependency extends ScanningRecipe<RemoveDependency.Accumulator> {
    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "com.fasterxml.jackson*")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "jackson-module*")
    String artifactId;

    @Option(displayName = "Unless using",
            description = "Do not remove if type is in use. Supports glob expressions.",
            example = "org.aspectj.lang.*",
            required = false)
    @Nullable
    String unlessUsing;

    // Gradle only parameter
    @Option(displayName = "The dependency configuration",
            description = "The dependency configuration to remove from.",
            example = "api",
            required = false)
    @Nullable
    String configuration;

    // Maven only parameter
    @Option(displayName = "Scope",
            description = "Only remove dependencies if they are in this scope. If 'runtime', this will" +
                          "also remove dependencies in the 'compile' scope because 'compile' dependencies are part of the runtime dependency set",
            valid = {"compile", "test", "runtime", "provided"},
            example = "compile",
            required = false)
    @Nullable
    String scope;

    String displayName = "Remove a Gradle or Maven dependency";

    String description = "For Gradle project, removes a single dependency from the dependencies section of the `build.gradle`.\n" +
               "For Maven project, removes a single dependency from the `<dependencies>` section of the pom.xml.";

    public static class Accumulator {
        final Map<JavaProject, Boolean> projectToInUse = new HashMap<>();
        final Map<UUID, JavaProject> mavenIdToProject = new HashMap<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        if (unlessUsing == null) {
            return TreeVisitor.noop();
        }
        UsesType<ExecutionContext> usesType = new UsesType<>(unlessUsing, true);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                tree.getMarkers().findFirst(JavaProject.class).ifPresent(javaProject -> {
                    acc.projectToInUse.compute(javaProject, (jp, foundSoFar) ->
                            Boolean.TRUE.equals(foundSoFar) || tree != usesType.visit(tree, ctx));
                    tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr ->
                            acc.mavenIdToProject.put(mrr.getId(), javaProject));
                });
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gradleRemoveDep = new org.openrewrite.gradle.RemoveDependency(groupId, artifactId, configuration).getVisitor();
            final TreeVisitor<?, ExecutionContext> mavenRemoveDep = new org.openrewrite.maven.RemoveDependency(groupId, artifactId, scope).getVisitor();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                if (unlessUsing != null) {
                    JavaProject jp = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                    if (jp == null || Boolean.TRUE.equals(acc.projectToInUse.get(jp))) {
                        return tree;
                    }
                    MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                    if (mrr != null && anyDescendantPreservesDependency(mrr, acc)) {
                        return tree;
                    }
                }
                SourceFile sf = (SourceFile) tree;
                if (gradleRemoveDep.isAcceptable(sf, ctx)) {
                    return gradleRemoveDep.visitNonNull(tree, ctx);
                }
                if (mavenRemoveDep.isAcceptable(sf, ctx)) {
                    return mavenRemoveDep.visitNonNull(tree, ctx);
                }
                return tree;
            }
        };
    }

    /**
     * Keep this pom's `<dependency>` declaration if any descendant module in the reactor uses the
     * `unlessUsing` type AND does not also declare the dependency itself. A descendant that
     * self-declares the dependency in its own pom is unaffected by removing it from an ancestor,
     * so it should not block removal.
     */
    private boolean anyDescendantPreservesDependency(MavenResolutionResult mrr, Accumulator acc) {
        for (MavenResolutionResult child : mrr.getModules()) {
            JavaProject childJp = acc.mavenIdToProject.get(child.getId());
            if (childJp != null && Boolean.TRUE.equals(acc.projectToInUse.get(childJp)) && !declaresDependency(child)) {
                return true;
            }
            if (anyDescendantPreservesDependency(child, acc)) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresDependency(MavenResolutionResult mrr) {
        // Look at the raw `requested` pom — the as-written `<dependencies>` of this module only,
        // excluding inheritance from a parent pom. A module that inherits the dep from the parent
        // we may be modifying should still block removal.
        for (Dependency d : mrr.getPom().getRequested().getDependencies()) {
            if (matchesGlob(d.getGroupId(), groupId) && matchesGlob(d.getArtifactId(), artifactId)) {
                return true;
            }
        }
        return false;
    }
}
