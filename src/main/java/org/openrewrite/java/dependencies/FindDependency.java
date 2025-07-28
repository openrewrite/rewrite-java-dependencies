/*
 * Copyright 2025 the original author or authors.
 *
 * Moderne Proprietary. Only for use by Moderne customers under the terms of a commercial contract.
 */
package org.openrewrite.java.dependencies;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindDependency extends Recipe {

    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate identifying its publisher.",
            example = "com.google.guava")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate uniquely identifying it among artifacts from the same publisher.",
            example = "guava")
    String artifactId;

    @Option(displayName = "Version",
            description = "An exact version number or node-style semver selector used to select the version number.",
            example = "3.0.0",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. " +
                          "So for example, setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    String versionPattern;

    @Option(displayName = "Configuration",
            description = "For Gradle only, the dependency configuration to search for dependencies in. If omitted then all configurations will be searched.",
            example = "api",
            required = false)
    @Nullable
    String configuration;

    @Override
    public String getDisplayName() {
        return "Find Maven and Gradle dependencies";
    }

    @Override
    public String getDescription() {
        return "Finds direct dependencies declared in Maven and Gradle build files. " +
               "This does *not* search transitive dependencies. " +
               "To detect both direct and transitive dependencies use `org.openrewrite.java.dependencies.DependencyInsight` " +
               "This recipe works for both Maven and Gradle projects.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {

            final TreeVisitor<?, ExecutionContext> mavenFindDependency = new org.openrewrite.maven.search.FindDependency(groupId,artifactId,version,versionPattern)
                    .getVisitor();
            final TreeVisitor<?, ExecutionContext> gradleFindDependency = new org.openrewrite.gradle.search.FindDependency(groupId, artifactId, configuration)
                    .getVisitor();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (mavenFindDependency.isAcceptable(s, ctx)) {
                    return mavenFindDependency.visitNonNull(tree, ctx);
                }
                if (gradleFindDependency.isAcceptable(s, ctx)) {
                    // Handle Gradle projects
                    return gradleFindDependency.visitNonNull(tree, ctx);
                }
                return s;
            }
        };
    }
}
