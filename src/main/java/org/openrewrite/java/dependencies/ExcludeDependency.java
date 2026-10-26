/*
 * Copyright 2026 the original author or authors.
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

@EqualsAndHashCode(callSuper = false)
@Value
public class ExcludeDependency extends Recipe {
    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. " +
                          "Use `*` to exclude the artifact from every group. Partial wildcards are not supported.",
            example = "commons-logging")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. " +
                          "Use `*` to exclude every artifact in the group. Partial wildcards are not supported.",
            example = "commons-logging")
    String artifactId;

    @Option(displayName = "Gradle configuration",
            description = "For Gradle only, only direct dependency declarations in this configuration are considered. " +
                          "If omitted, declarations in every configuration are considered.",
            example = "implementation",
            required = false)
    @Nullable
    String configuration;

    @Option(displayName = "Maven scope",
            description = "For Maven only, only dependencies in this scope are considered. If you specify `compile`, this will NOT match dependencies in `runtime`.",
            valid = {"compile", "test", "runtime", "provided"},
            example = "compile",
            required = false)
    @Nullable
    String scope;

    String displayName = "Exclude a Gradle or Maven dependency";

    String description = "Exclude the specified dependency from any direct dependency that transitively includes it. " +
                         "For a Gradle project, adds an `exclude` to the declaration's closure in `build.gradle` or `build.gradle.kts`. " +
                         "For a Maven project, adds an `<exclusion>` to the dependency in the `pom.xml`.";

    @Override
    public String getInstanceNameSuffix() {
        // Null when rendering the descriptor of an unconfigured recipe
        if (groupId == null || artifactId == null) {
            return "";
        }
        return String.format("`%s:%s`", groupId, artifactId);
    }

    @Override
    public Validated<Object> validate() {
        return super.validate()
                .and(gradleExcludeDependency().validate())
                .and(mavenExcludeDependency().validate());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gradleExcludeDep = gradleExcludeDependency().getVisitor();
            final TreeVisitor<?, ExecutionContext> mavenExcludeDep = mavenExcludeDependency().getVisitor();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                // Both delegates skip source files they don't apply to
                Tree t = gradleExcludeDep.visitNonNull(tree, ctx);
                return mavenExcludeDep.visitNonNull(t, ctx);
            }
        };
    }

    private org.openrewrite.gradle.ExcludeDependency gradleExcludeDependency() {
        return new org.openrewrite.gradle.ExcludeDependency(groupId, artifactId, configuration);
    }

    private org.openrewrite.maven.ExcludeDependency mavenExcludeDependency() {
        return new org.openrewrite.maven.ExcludeDependency(groupId, artifactId, scope);
    }
}
