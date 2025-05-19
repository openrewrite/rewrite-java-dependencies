/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.maven.AddManagedDependency;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpgradeTransitiveDependencyVersion extends ScanningRecipe<UpgradeTransitiveDependencyVersion.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Upgrade transitive Gradle or Maven dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrades the version of a transitive dependency in a Maven pom.xml or Gradle build.gradle. " +
               "Leaves direct dependencies unmodified. " +
               "Can be paired with the regular Upgrade Dependency Version recipe to upgrade a dependency everywhere, " +
               "regardless of whether it is direct or transitive.";
    }

    @Option(displayName = "Group",
            description = "The first part of a dependency coordinate 'org.apache.logging.log4j:ARTIFACT_ID:VERSION'.",
            example = "org.apache.logging.log4j")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The second part of a dependency coordinate 'org.apache.logging.log4j:log4j-bom:VERSION'.",
            example = "log4j-bom")
    String artifactId;

    @Option(displayName = "Version",
            description = "An exact version number or node-style semver selector used to select the version number.",
            example = "latest.release")
    String version;

    @Option(displayName = "Scope",
            description = "An optional scope to use for the dependency management tag. Relevant only to Maven.",
            example = "import",
            valid = {"import", "runtime", "provided", "test"},
            required = false)
    @Nullable
    String scope;

    @Option(displayName = "Type",
            description = "An optional type to use for the dependency management tag. Relevant only to Maven builds.",
            valid = {"jar", "pom", "war"},
            example = "pom",
            required = false)
    @Nullable
    String type;

    @Option(displayName = "Classifier",
            description = "An optional classifier to use for the dependency management tag. Relevant only to Maven.",
            example = "test",
            required = false)
    @Nullable
    String classifier;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example," +
                          "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    String versionPattern;

    @Option(displayName = "Because",
            description = "The reason for upgrading the transitive dependency. For example, we could be responding to a vulnerability.",
            required = false,
            example = "CVE-2021-1234")
    @Nullable
    String because;

    @Option(displayName = "Releases only",
            description = "Whether to exclude snapshots from consideration when using a semver selector",
            required = false)
    @Nullable
    Boolean releasesOnly;

    @Option(displayName = "Only if using glob expression for group:artifact",
            description = "Only add managed dependencies to projects having a dependency matching the expression.",
            example = "org.apache.logging.log4j:log4j*",
            required = false)
    @Nullable
    String onlyIfUsing;

    @Option(displayName = "Add to the root pom",
            description = "Add to the root pom where root is the eldest parent of the pom within the source set.",
            required = false)
    @Nullable
    Boolean addToRootPom;

    @Value
    public static class Accumulator {
        AddManagedDependency.Scanned mavenAccumulator;
        org.openrewrite.gradle.UpgradeTransitiveDependencyVersion.DependencyVersionState gradleAccumulator;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(getMavenUpgradeTransitive().getInitialValue(ctx), getGradleUpgradeTransitive().getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> gradleUTDV = getGradleUpgradeTransitive().getScanner(acc.gradleAccumulator);
        TreeVisitor<?, ExecutionContext> mavenUTDV = getMavenUpgradeTransitive().getScanner(acc.mavenAccumulator);

        return delegate(gradleUTDV, mavenUTDV);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> gradleUTDV = getGradleUpgradeTransitive().getVisitor(acc.gradleAccumulator);
        TreeVisitor<?, ExecutionContext> mavenUTDV = getMavenUpgradeTransitive().getVisitor(acc.mavenAccumulator);

        return delegate(gradleUTDV, mavenUTDV);
    }

    private TreeVisitor<Tree, ExecutionContext> delegate(TreeVisitor<?, ExecutionContext> gradle, TreeVisitor<?, ExecutionContext> maven) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return gradle.isAcceptable(sourceFile, ctx) || maven.isAcceptable(sourceFile, ctx);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile t = (SourceFile) tree;
                if (gradle.isAcceptable(t, ctx)) {
                    t = (SourceFile) gradle.visitNonNull(t, ctx);
                } else if (maven.isAcceptable(t, ctx)) {
                    t = (SourceFile) maven.visitNonNull(t, ctx);
                }
                return t;
            }
        };
    }

    private org.openrewrite.gradle.UpgradeTransitiveDependencyVersion getGradleUpgradeTransitive() {
        return new org.openrewrite.gradle.UpgradeTransitiveDependencyVersion(groupId, artifactId, version, versionPattern, because, null);
    }

    private org.openrewrite.maven.UpgradeTransitiveDependencyVersion getMavenUpgradeTransitive() {
        return new org.openrewrite.maven.UpgradeTransitiveDependencyVersion(groupId, artifactId, version, scope, type, classifier, versionPattern, releasesOnly, onlyIfUsing, addToRootPom);
    }
}
