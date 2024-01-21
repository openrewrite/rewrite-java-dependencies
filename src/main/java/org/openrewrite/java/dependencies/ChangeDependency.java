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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChangeDependency extends Recipe {
    // Gradle and Maven shared parameters
    @Option(displayName = "Old groupId",
            description = "The old groupId to replace. The groupId is the first part of a dependency coordinate 'com.google.guava:guava:VERSION'. Supports glob expressions.",
            example = "org.openrewrite.recipe")
    private final String oldGroupId;

    @Option(displayName = "Old artifactId",
            description = "The old artifactId to replace. The artifactId is the second part of a dependency coordinate 'com.google.guava:guava:VERSION'. Supports glob expressions.",
            example = "rewrite-testing-frameworks")
    private final String oldArtifactId;

    @Option(displayName = "New groupId",
            description = "The new groupId to use. Defaults to the existing group id.",
            example = "corp.internal.openrewrite.recipe",
            required = false)
    @Nullable
    private final String newGroupId;

    @Option(displayName = "New artifactId",
            description = "The new artifactId to use. Defaults to the existing artifact id.",
            example = "rewrite-testing-frameworks",
            required = false)
    @Nullable
    private final String newArtifactId;

    @Option(displayName = "New version",
            description = "An exact version number or node-style semver selector used to select the version number.",
            example = "29.X",
            required = false)
    @Nullable
    private final String newVersion;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example," +
                          "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    private final String versionPattern;

    @Option(displayName = "Override managed version",
            description = "If the new dependency has a managed version, this flag can be used to explicitly set the version on the dependency. The default for this flag is `false`.",
            required = false)
    @Nullable
    private final Boolean overrideManagedVersion;

    @Override
    public String getDisplayName() {
        return "Change Gradle or Maven dependency";
    }

    @Override
    public String getDescription() {
        return "Change the groupId, artifactId and/or the version of a specified Gradle or Maven dependency.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final TreeVisitor<?, ExecutionContext> gradleVisitor = new org.openrewrite.gradle.ChangeDependency(
                    oldGroupId, oldArtifactId, newGroupId, newArtifactId, newVersion, versionPattern, overrideManagedVersion)
                    .getVisitor();
            private final TreeVisitor<?, ExecutionContext> mavenVisitor = new org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId(
                    oldGroupId, oldArtifactId, newGroupId, newArtifactId, newVersion, versionPattern, overrideManagedVersion)
                    .getVisitor();

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
