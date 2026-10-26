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
public class RemoveExclusion extends Recipe {
    @Option(displayName = "Group ID",
            description = "The group of the excluded dependency. If omitted (or `*`), exclusions of the artifact from any group are removed. " +
                          "Partial wildcards are not supported.",
            example = "org.springframework.boot",
            required = false)
    @Nullable
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The artifact of the excluded dependency. If omitted (or `*`), exclusions of any artifact in the group are removed, " +
                          "including those of the whole group. Partial wildcards are not supported.",
            example = "spring-boot-starter-tomcat",
            required = false)
    @Nullable
    String artifactId;

    String displayName = "Remove a Gradle or Maven dependency exclusion";

    String description = "Remove exclusions of the specified dependency, the inverse of `ExcludeDependency`. " +
                         "For a Gradle project, removes `exclude` declarations from dependency and configuration closures in `build.gradle` or `build.gradle.kts`. " +
                         "For a Maven project, removes the `<exclusion>` from dependencies and managed dependencies in the `pom.xml`.";

    @Override
    public String getInstanceNameSuffix() {
        return String.format("`%s:%s`", wildcardIfNull(groupId), wildcardIfNull(artifactId));
    }

    @Override
    public Validated<Object> validate() {
        return super.validate()
                .and(gradleRemoveExclusion().validate())
                .and(mavenRemoveExclusion().validate());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gradleRemoveExclusion = gradleRemoveExclusion().getVisitor();
            final TreeVisitor<?, ExecutionContext> mavenRemoveExclusion = mavenRemoveExclusion().getVisitor();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                // Both delegates skip source files they don't apply to
                Tree t = gradleRemoveExclusion.visitNonNull(tree, ctx);
                return mavenRemoveExclusion.visitNonNull(t, ctx);
            }
        };
    }

    private org.openrewrite.gradle.RemoveExclusion gradleRemoveExclusion() {
        return new org.openrewrite.gradle.RemoveExclusion(groupId, artifactId);
    }

    private org.openrewrite.maven.RemoveExclusion mavenRemoveExclusion() {
        return new org.openrewrite.maven.RemoveExclusion("*", "*", wildcardIfNull(groupId), wildcardIfNull(artifactId), null);
    }

    private static String wildcardIfNull(@Nullable String coordinate) {
        return coordinate == null ? "*" : coordinate;
    }
}
