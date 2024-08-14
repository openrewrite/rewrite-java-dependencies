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
import org.openrewrite.maven.tree.Scope;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddDependency extends ScanningRecipe<AddDependency.Accumulator> {
    // Gradle and Maven shared parameters
    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`.",
            example = "com.google.guava")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`",
            example = "guava")
    String artifactId;

    @Option(displayName = "Version",
            description = "An exact version number or node-style semver selector used to select the version number.",
            example = "29.X",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example, " +
                          "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    String versionPattern;

    @Option(displayName = "Only if using",
            description = "Used to determine if the dependency will be added and in which scope it should be placed.",
            example = "org.junit.jupiter.api.*",
            required = false)
    @Nullable
    String onlyIfUsing;

    @Option(displayName = "Classifier",
            description = "A classifier to add. Commonly used to select variants of a library.",
            example = "test",
            required = false)
    @Nullable
    String classifier;

    @Option(displayName = "Family pattern",
            description = "A pattern, applied to groupIds, used to determine which other dependencies should have aligned version numbers. " +
                          "Accepts '*' as a wildcard character.",
            example = "com.fasterxml.jackson*",
            required = false)
    @Nullable
    String familyPattern;

    // Gradle only parameters
    @Option(displayName = "Extension",
            description = "For Gradle only, The extension of the dependency to add. If omitted Gradle defaults to assuming the type is \"jar\".",
            example = "jar",
            required = false)
    @Nullable
    String extension;

    @Option(displayName = "Gradle configuration",
            description = "The Gradle dependency configuration name within which to place the dependency. " +
                          "When omitted the configuration will be determined by the Maven scope parameter. " +
                          "If that parameter is also omitted, configuration will be determined based on where types " +
                          "matching `onlyIfUsing` appear in source code.",
            example = "implementation",
            required = false)
    @Nullable
    String configuration;

    // Maven only parameters
    @Option(displayName = "Maven scope",
            description = "The Maven scope within which to place the dependency. " +
                          "When omitted scope will be determined based on where types matching `onlyIfUsing` appear in source code.",
            example = "runtime",
            valid = {"compile", "provided", "runtime", "test"},
            required = false)
    @Nullable
    String scope;

    @Option(displayName = "Releases only",
            description = "For Maven only, Whether to exclude snapshots from consideration when using a semver selector",
            required = false)
    @Nullable
    Boolean releasesOnly;

    @Option(displayName = "Type",
            description = "For Maven only, The type of dependency to add. If omitted Maven defaults to assuming the type is \"jar\".",
            valid = {"jar", "pom", "war"},
            example = "jar",
            required = false)
    @Nullable
    String type;

    @Option(displayName = "Optional",
            description = "Set the value of the `<optional>` tag. No `<optional>` tag will be added when this is `null`.",
            required = false)
    @Nullable
    Boolean optional;

    @Option(displayName = "Accept transitive",
            description = "Default false. If enabled, the dependency will not be added if it is already on the classpath as a transitive dependency.",
            example = "true",
            required = false)
    @Nullable
    Boolean acceptTransitive;

    @Override
    public String getDisplayName() {
        return "Add Gradle or Maven dependency";
    }

    @Override
    public String getDescription() {
        return "For a Gradle project, add a gradle dependency to a `build.gradle` file in the correct configuration " +
               "based on where it is used. Or For a maven project, Add a Maven dependency to a `pom.xml` file in the " +
               "correct scope based on where it is used.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(gradleAddDep().getInitialValue(ctx), mavenAddDep().getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                gradleAddDep().getScanner(acc.gradleAccumulator).visit(tree, ctx);
                mavenAddDep().getScanner(acc.mavenAccumulator).visit(tree, ctx);
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gradleAddDep = gradleAddDep().getVisitor(acc.gradleAccumulator);
            final TreeVisitor<?, ExecutionContext> mavenAddDep = mavenAddDep().getVisitor(acc.mavenAccumulator);

            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                Tree t = tree;
                if (gradleAddDep.isAcceptable((SourceFile) t, ctx)) {
                    t = gradleAddDep.visitNonNull(tree, ctx);
                }
                if (mavenAddDep.isAcceptable((SourceFile) t, ctx)) {
                    t = mavenAddDep.visitNonNull(tree, ctx);
                }
                return t;
            }
        };
    }

    @Value
    public static class Accumulator {
        org.openrewrite.gradle.AddDependency.Scanned gradleAccumulator;
        org.openrewrite.maven.AddDependency.Scanned mavenAccumulator;
    }

    private org.openrewrite.gradle.AddDependency gradleAddDep() {
        String configurationName = null;
        if(configuration != null) {
            configurationName = configuration;
        } else if(scope != null) {
            configurationName = Scope.asGradleConfigurationName(Scope.fromName(scope));
        }
        return new org.openrewrite.gradle.AddDependency(groupId, artifactId, version, versionPattern,
                configurationName, onlyIfUsing, classifier, extension, familyPattern, acceptTransitive);
    }

    private org.openrewrite.maven.AddDependency mavenAddDep() {
        return new org.openrewrite.maven.AddDependency(groupId, artifactId, version != null ? version : "latest.release",
                versionPattern, scope, releasesOnly, onlyIfUsing, type, classifier, optional, familyPattern,
                acceptTransitive);
    }
}
