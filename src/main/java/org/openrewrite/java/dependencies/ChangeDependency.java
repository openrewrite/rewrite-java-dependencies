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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeDependency extends ScanningRecipe<ChangeDependency.Accumulator> {
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

    String displayName = "Change Gradle or Maven dependency";

    String description = "Change the group ID, artifact ID, and/or the version of a specified Gradle or Maven dependency.";

    @Override
    public Validated<Object> validate(ExecutionContext ctx) {
        return super.validate(ctx)
                .and(((Recipe) getGradleChangeDependency()).validate())
                .and(((Recipe) getMavenChangeDependency()).validate());
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(
                getMavenChangeDependency().getInitialValue(ctx),
                getGradleChangeDependency().getInitialValue(ctx)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> mavenScanner = getMavenChangeDependency().getScanner(acc.mavenAccumulator);
        TreeVisitor<?, ExecutionContext> gradleScanner = getGradleChangeDependency().getScanner(acc.gradleAccumulator);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return mavenScanner.isAcceptable(sourceFile, ctx) || gradleScanner.isAcceptable(sourceFile, ctx);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                SourceFile s = (SourceFile) requireNonNull(tree);
                if (gradleScanner.isAcceptable(s, ctx)) {
                    return gradleScanner.visit(tree, ctx);
                }
                if (mavenScanner.isAcceptable(s, ctx)) {
                    return mavenScanner.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> mavenVisitor = getMavenChangeDependency().getVisitor(acc.mavenAccumulator);
        TreeVisitor<?, ExecutionContext> gradleVisitor = getGradleChangeDependency().getVisitor(acc.gradleAccumulator);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return mavenVisitor.isAcceptable(sourceFile, ctx) || gradleVisitor.isAcceptable(sourceFile, ctx);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (gradleVisitor.isAcceptable(s, ctx)) {
                    s = (SourceFile) gradleVisitor.visitNonNull(s, ctx);
                }
                if (mavenVisitor.isAcceptable(s, ctx)) {
                    s = (SourceFile) mavenVisitor.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }

    org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId getMavenChangeDependency() {
        return new org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId(
                oldGroupId, oldArtifactId, newGroupId, newArtifactId,
                newVersion, versionPattern, overrideManagedVersion, changeManagedDependency);
    }

    org.openrewrite.gradle.ChangeDependency getGradleChangeDependency() {
        return new org.openrewrite.gradle.ChangeDependency(
                oldGroupId, oldArtifactId, newGroupId, newArtifactId,
                newVersion, versionPattern, overrideManagedVersion, true);
    }

    @Data
    public static final class Accumulator {
        private final org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId.Accumulator mavenAccumulator;
        private final org.openrewrite.gradle.ChangeDependency.Accumulator gradleAccumulator;
    }
}
