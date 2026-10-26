/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.dependencies.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class DoesNotIncludeDependency extends Recipe {
    String displayName = "Does not include Gradle or Maven dependency";

    String description = "A precondition which returns false if visiting a Gradle build file or Maven `pom.xml` which includes the specified dependency in the classpath of some Gradle configuration or Maven scope. " +
                "For compatibility with multi-module projects, this should most often be applied as a precondition.";

    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. Supports glob expressions.",
            example = "com.google.guava")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. Supports glob expressions.",
            example = "guava")
    String artifactId;

    @Option(displayName = "Version",
            description = "Match only dependencies with the specified resolved version. " +
                    "Node-style [version selectors](https://docs.openrewrite.org/reference/dependency-version-selectors) may be used. " +
                    "All versions are searched by default.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Only direct dependencies",
            description = "For Maven only, if enabled, transitive dependencies will not be considered. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean onlyDirect;

    @Option(displayName = "Maven scope",
            description = "For Maven only, if specified, only the requested scope's classpaths will be checked. All scopes are checked by default.",
            required = false,
            valid = {"compile", "test", "runtime", "provided"},
            example = "compile")
    @Nullable
    String scope;

    @Option(displayName = "Gradle configuration",
            description = "For Gradle only, match dependencies with the specified configuration. If not specified, all configurations will be searched.",
            example = "compileClasspath",
            required = false)
    @Nullable
    String configuration;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gdnid = new org.openrewrite.gradle.search.DoesNotIncludeDependency(
                    groupId, artifactId, version, configuration)
                    .getVisitor();
            final TreeVisitor<?, ExecutionContext> mdnid = new org.openrewrite.maven.search.DoesNotIncludeDependency(
                    groupId, artifactId, version, onlyDirect, scope)
                    .getVisitor();

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return gdnid.isAcceptable(sourceFile, ctx) || mdnid.isAcceptable(sourceFile, ctx);
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (gdnid.isAcceptable(s, ctx)) {
                    s = (SourceFile) gdnid.visitNonNull(s, ctx);
                } else if (mdnid.isAcceptable(s, ctx)) {
                    s = (SourceFile) mdnid.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }
}
