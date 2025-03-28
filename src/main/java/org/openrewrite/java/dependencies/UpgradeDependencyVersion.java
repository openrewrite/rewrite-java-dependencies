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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

import java.util.List;

import static java.util.Objects.requireNonNull;


@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UpgradeDependencyVersion extends ScanningRecipe<UpgradeDependencyVersion.Accumulator> {
    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "com.fasterxml.jackson*")
    private final String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "jackson-module*")
    private final String artifactId;

    @Option(displayName = "New version",
            description = "An exact version number or node-style semver selector used to select the version number. ",
            example = "29.X")
    private final String newVersion;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example," +
                          "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    private final String versionPattern;

    @Option(displayName = "Override managed version",
            description = "For Maven project only, This flag can be set to explicitly override a managed " +
                          "dependency's version. The default for this flag is `false`.",
            required = false)
    @Nullable
    private final Boolean overrideManagedVersion;

    @Option(displayName = "Retain versions",
            description = "For Maven project only, accepts a list of GAVs. For each GAV, if it is a project direct dependency, and it is removed " +
                          "from dependency management after the changes from this recipe, then it will be retained with an explicit version. " +
                          "The version can be omitted from the GAV to use the old value from dependency management.",
            example = "com.jcraft:jsch",
            required = false)
    @Nullable
    private final List<String> retainVersions;

    @Override
    public String getDisplayName() {
        return "Upgrade Gradle or Maven dependency versions";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "For Gradle projects, upgrade the version of a dependency in a `build.gradle` file. " +
               "Supports updating dependency declarations of various forms:\n" +
               "* `String` notation: `\"group:artifact:version\"` \n" +
               "* `Map` notation: `group: 'group', name: 'artifact', version: 'version'`\n" +
               "It is possible to update version numbers which are defined earlier in the same file in variable declarations.\n\n" +
               "For Maven projects, upgrade the version of a dependency by specifying a group ID and (optionally) an " +
               "artifact ID using Node Semver advanced range selectors, allowing more precise control over version " +
               "updates to patch or minor releases.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(
                getUpgradeMavenDependencyVersion().getInitialValue(ctx),
                getUpgradeGradleDependencyVersion().getInitialValue(ctx)
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> mavenScanner = getUpgradeMavenDependencyVersion().getScanner(acc.mavenAccumulator);
        TreeVisitor<?, ExecutionContext> gradleScanner = getUpgradeGradleDependencyVersion().getScanner(acc.gradleAccumulator);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return mavenScanner.isAcceptable(sourceFile, ctx) || gradleScanner.isAcceptable(sourceFile, ctx);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (mavenScanner.isAcceptable((SourceFile) requireNonNull(tree), ctx)) {
                    return mavenScanner.visit(tree, ctx);
                } else if (gradleScanner.isAcceptable((SourceFile) tree, ctx)) {
                    return gradleScanner.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> mavenVisitor = getUpgradeMavenDependencyVersion().getVisitor(acc.mavenAccumulator);
        TreeVisitor<?, ExecutionContext> gradleVisitor = getUpgradeGradleDependencyVersion().getVisitor(acc.gradleAccumulator);
        return new TreeVisitor<Tree, ExecutionContext>() {

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return mavenVisitor.isAcceptable(sourceFile, ctx) || gradleVisitor.isAcceptable(sourceFile, ctx);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree t = tree;
                assert tree != null;
                if (mavenVisitor.isAcceptable((SourceFile) tree, ctx)) {
                    t = mavenVisitor.visit(tree, ctx);
                } else if (gradleVisitor.isAcceptable((SourceFile) tree, ctx)) {
                    t = gradleVisitor.visit(t, ctx);
                }
                return t;
            }
        };
    }

    org.openrewrite.maven.UpgradeDependencyVersion getUpgradeMavenDependencyVersion() {
        return new org.openrewrite.maven.UpgradeDependencyVersion(groupId, artifactId, newVersion, versionPattern, overrideManagedVersion, retainVersions);
    }

    public org.openrewrite.gradle.UpgradeDependencyVersion getUpgradeGradleDependencyVersion() {
        return new org.openrewrite.gradle.UpgradeDependencyVersion(groupId, artifactId, newVersion, versionPattern);
    }

    @Data
    public static final class Accumulator {
        private final org.openrewrite.maven.UpgradeDependencyVersion.Accumulator mavenAccumulator;
        private final org.openrewrite.gradle.UpgradeDependencyVersion.DependencyVersionState gradleAccumulator;
    }
}
