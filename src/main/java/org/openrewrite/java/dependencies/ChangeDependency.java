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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ChangeDependency extends Recipe {
    // Gradle and Maven shared parameters
    @Option(displayName = "Old group ID",
            description = "The old group ID to replace. The group ID is the first part of a dependency coordinate 'com.google.guava:guava:VERSION'. Supports glob expressions.",
            example = "org.openrewrite.recipe")
    String oldGroupId;

    @Option(displayName = "Old artifact ID",
            description = "The old artifact ID to replace. The artifact ID is the second part of a dependency coordinate 'com.google.guava:guava:VERSION'. Supports glob expressions.",
            example = "rewrite-testing-frameworks")
    String oldArtifactId;

    @Option(displayName = "New group ID",
            description = "The new group ID to use. Defaults to the existing group ID.",
            example = "corp.internal.openrewrite.recipe",
            required = false)
    @Nullable
    String newGroupId;

    @Option(displayName = "New artifact ID",
            description = "The new artifact ID to use. Defaults to the existing artifact ID.",
            example = "rewrite-testing-frameworks",
            required = false)
    @Nullable
    String newArtifactId;

    @Option(displayName = "New version",
            description = "An exact version number or node-style semver selector used to select the version number.",
            example = "29.X",
            required = false)
    @Nullable
    String newVersion;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example," +
                          "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    String versionPattern;

    @Option(displayName = "Override managed version",
            description = "If the new dependency has a managed version, this flag can be used to explicitly set the version on the dependency. The default for this flag is `false`.",
            required = false)
    @Nullable
    Boolean overrideManagedVersion;

    @Option(displayName = "Update dependency management",
            description = "Also update the dependency management section. The default for this flag is `true`.",
            required = false)
    @Nullable
    Boolean changeManagedDependency;

    @Override
    public String getDisplayName() {
        return "Change Gradle or Maven dependency";
    }

    @Override
    public String getDescription() {
        return "Change the group ID, artifact ID, and/or the version of a specified Gradle or Maven dependency.";
    }

    @Override
    public Validated<Object> validate(ExecutionContext ctx) {
        return super.validate(ctx)
                .and(((Recipe) new org.openrewrite.gradle.ChangeDependency(
                        oldGroupId, oldArtifactId, newGroupId, newArtifactId, newVersion, versionPattern, overrideManagedVersion)).validate())
                .and(((Recipe) new org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId(
                        oldGroupId, oldArtifactId, newGroupId, newArtifactId, newVersion, versionPattern, overrideManagedVersion, changeManagedDependency)).validate());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> mavenVisitor = new org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId(
                    oldGroupId, oldArtifactId,
                    newGroupId, newArtifactId,
                    newVersion, versionPattern,
                    overrideManagedVersion, changeManagedDependency).getVisitor();
            final TreeVisitor<?, ExecutionContext> gradleVisitor = new org.openrewrite.gradle.ChangeDependency(
                    oldGroupId, oldArtifactId,
                    newGroupId, newArtifactId,
                    newVersion, versionPattern,
                    overrideManagedVersion).getVisitor();

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
                return mavenVisitor.isAcceptable(sourceFile, executionContext) || gradleVisitor.isAcceptable(sourceFile, executionContext);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (gradleVisitor.isAcceptable(s, ctx)) {
                    s = (SourceFile) gradleVisitor.visitNonNull(s, ctx);
                } else if (mavenVisitor.isAcceptable(s, ctx)) {
                    s = (SourceFile) mavenVisitor.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }
}
