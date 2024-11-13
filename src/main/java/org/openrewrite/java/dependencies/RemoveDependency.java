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
import org.openrewrite.java.search.UsesType;

import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveDependency extends ScanningRecipe<AtomicBoolean> {
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

    @Override
    public String getDisplayName() {
        return "Remove a Gradle or Maven dependency";
    }

    @Override
    public String getDescription() {
        return "For Gradle project, removes a single dependency from the dependencies section of the `build.gradle`.\n" +
               "For Maven project, removes a single dependency from the <dependencies> section of the pom.xml.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    org.openrewrite.gradle.@Nullable RemoveDependency removeGradleDependency;

    org.openrewrite.maven.@Nullable RemoveDependency removeMavenDependency;

    public RemoveDependency(
            String groupId,
            String artifactId,
            @Nullable String unlessUsing,
            @Nullable String configuration,
            @Nullable String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.unlessUsing = unlessUsing;
        this.configuration = configuration;
        this.scope = scope;
        removeGradleDependency = new org.openrewrite.gradle.RemoveDependency(groupId, artifactId, configuration);
        removeMavenDependency = new org.openrewrite.maven.RemoveDependency(groupId, artifactId, scope);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean usageFound) {
        if (unlessUsing == null) {
            return TreeVisitor.noop();
        }
        UsesType<ExecutionContext> usesType = new UsesType<>(unlessUsing, true);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (!usageFound.get()) {
                    usageFound.set(tree != usesType.visit(tree, ctx));
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean usageFound) {
        if (usageFound.get()) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gradleRemoveDep = removeGradleDependency.getVisitor();
            final TreeVisitor<?, ExecutionContext> mavenRemoveDep = removeMavenDependency.getVisitor();

            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile sf = (SourceFile) tree;
                if (gradleRemoveDep.isAcceptable(sf, ctx)) {
                    return gradleRemoveDep.visitNonNull(tree, ctx);
                }
                if (mavenRemoveDep.isAcceptable(sf, ctx)) {
                    return mavenRemoveDep.visitNonNull(tree, ctx);
                }
                return sf;
            }
        };
    }
}
