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
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.Semver;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class ModuleHasDependency extends ScanningRecipe<Set<JavaProject>> {

    String displayName = "Module has dependency";

    String description = "Searches for both Gradle and Maven modules that have a dependency matching the specified `groupId` and `artifactId`. " +
                "Places a `SearchResult` marker on all sources within a module with a matching dependency. " +
                "This recipe is intended to be used as a precondition for other recipes. " +
                "For example this could be used to limit the application of a Spring Boot migration to only projects " +
                "that use `spring-boot-starter`, limiting unnecessary upgrading. " +
                "If the search result you want is instead just the `build.gradle(.kts)` or `pom.xml` file declaring the dependency, use the `FindDependency` recipe instead.";

    @Option(displayName = "Group pattern",
            description = "Group glob pattern used to match dependencies.",
            example = "com.fasterxml.jackson.module")
    String groupIdPattern;

    @Option(displayName = "Artifact pattern",
            description = "Artifact glob pattern used to match dependencies.",
            example = "jackson-module-*")
    String artifactIdPattern;

    @Option(displayName = "Maven scope",
            description = "For Maven only, match dependencies with the specified scope. All scopes are searched by default.",
            valid = {"compile", "test", "runtime", "provided", "system"},
            example = "compile",
            required = false)
    @Nullable
    String scope;

    @Option(displayName = "Version",
            description = "Match only dependencies with the specified version. " +
                    "Node-style [version selectors](https://docs.openrewrite.org/reference/dependency-version-selectors) may be used. " +
                    "All versions are searched by default.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Only direct",
            description = "If enabled, transitive dependencies will not be considered. All dependencies are searched by default.",
            required = false)
    @Nullable
    Boolean onlyDirect;

    @Option(displayName = "Invert marking",
            description = "If `true`, will invert the check for whether to mark a file. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean invertMarking;

    @Override
    public Validated<Object> validate() {
        Validated<Object> validated = super.validate()
                .and(Validated.test("scope", "scope is a valid Maven scope", scope,
                        s -> Scope.fromName(s) != Scope.Invalid));
        if (version != null) {
            validated = validated.and(Semver.validate(version, null));
        }
        return validated;
    }

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> acc) {
        HasDependencyMatcher matcher = new HasDependencyMatcher(groupIdPattern, artifactIdPattern, scope, version, Boolean.TRUE.equals(onlyDirect));
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                tree.getMarkers()
                        .findFirst(JavaProject.class)
                        .ifPresent(jp -> {
                            if (!acc.contains(jp) && matcher.hasDependency(tree)) {
                                acc.add(jp);
                            }
                        });
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                boolean shouldInvert = Boolean.TRUE.equals(invertMarking);
                String dependencyGav = groupIdPattern + ":" + artifactIdPattern + (version == null ? "" : ":" + version);
                Optional<JavaProject> maybeJp = tree.getMarkers().findFirst(JavaProject.class);
                if (!maybeJp.isPresent()) {
                    if (shouldInvert) {
                        return SearchResult.found(tree, "No module, so vacuously does not have dependency: " + dependencyGav);
                    }
                    return tree;
                }
                JavaProject jp = maybeJp.get();
                if (shouldInvert && !acc.contains(jp)) {
                    return SearchResult.found(tree, "Module does not have dependency: " + dependencyGav);
                }
                if (!shouldInvert && acc.contains(jp)) {
                    return SearchResult.found(tree, "Module has dependency: " + dependencyGav);
                }
                return tree;
            }
        };
    }
}
