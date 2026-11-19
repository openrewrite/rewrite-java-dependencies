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
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.semver.Semver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.disjoint;
import static java.util.Collections.emptySet;

@EqualsAndHashCode(callSuper = false)
@Value
public class ModuleHasDependency extends ScanningRecipe<Set<JavaProject>> {

    String displayName = "Module has dependency";

    String description = "Searches for both Gradle and Maven modules that have a dependency matching the specified `groupId` and `artifactId`. " +
                "Places a `SearchResult` marker on all sources within a module with a matching dependency. " +
                "A source without a module marker, such as a file outside the module's source sets, belongs to the innermost module whose directory holds it. " +
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
        return new ModulesWithDependency();
    }

    /**
     * Kept a {@code Set<JavaProject>} so that recipes reusing this recipe's scanner with their own set keep working.
     */
    private static class ModulesWithDependency extends HashSet<JavaProject> {
        private static final Path ROOT = Paths.get("");

        private final Map<Path, Set<JavaProject>> modulesByBuildFileDirectory = new HashMap<>();
        private final Map<Path, Set<JavaProject>> modulesBySourceSetDirectory = new HashMap<>();

        /**
         * A module's directory is the one holding its build file or its {@code src/<source set>} directory. The second
         * places a module whose build file is not in the LST, such as a Gradle subproject configured by its parent.
         * A build file without a module marker, such as the POM of a project outside the build's reactor, still bounds
         * a directory, whose files then belong to no module.
         */
        void addModuleDirectory(SourceFile sourceFile, @Nullable JavaProject jp) {
            Markers markers = sourceFile.getMarkers();
            Path path = sourceFile.getSourcePath();
            JavaSourceSet sourceSet = jp == null ? null : markers.findFirst(JavaSourceSet.class).orElse(null);
            if (sourceSet != null) {
                for (int i = 0; i < path.getNameCount() - 2; i++) {
                    if ("src".equals(path.getName(i).toString()) && sourceSet.getName().equals(path.getName(i + 1).toString())) {
                        modulesBySourceSetDirectory.computeIfAbsent(i == 0 ? ROOT : path.subpath(0, i), d -> new HashSet<>()).add(jp);
                        return;
                    }
                }
            } else if (isBuildFile(path, markers, jp)) {
                Set<JavaProject> modules = modulesByBuildFileDirectory.computeIfAbsent(directoryOf(path), d -> new HashSet<>());
                if (jp != null) {
                    modules.add(jp);
                }
            }
        }

        private static boolean isBuildFile(Path path, Markers markers, @Nullable JavaProject jp) {
            String fileName = String.valueOf(path.getFileName());
            return "pom.xml".equals(fileName) || "build.gradle".equals(fileName) || "build.gradle.kts".equals(fileName) ||
                   jp != null && (markers.findFirst(MavenResolutionResult.class).isPresent() || markers.findFirst(GradleProject.class).isPresent());
        }

        Set<JavaProject> enclosingModules(Path sourcePath) {
            for (Path dir = directoryOf(sourcePath); ; dir = directoryOf(dir)) {
                Set<JavaProject> modules = modulesByBuildFileDirectory.get(dir);
                if (modules == null || modules.isEmpty()) {
                    modules = modulesBySourceSetDirectory.getOrDefault(dir, modules);
                }
                if (modules != null) {
                    return modules;
                }
                if (ROOT.equals(dir)) {
                    return emptySet();
                }
            }
        }

        private static Path directoryOf(Path path) {
            Path parent = path.getParent();
            return parent == null ? ROOT : parent;
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> acc) {
        HasDependencyMatcher matcher = new HasDependencyMatcher(groupIdPattern, artifactIdPattern, scope, version, Boolean.TRUE.equals(onlyDirect));
        @Nullable ModulesWithDependency moduleDirectories = acc instanceof ModulesWithDependency ? (ModulesWithDependency) acc : null;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                JavaProject jp = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (jp != null && !acc.contains(jp) && matcher.hasDependency(tree)) {
                    acc.add(jp);
                }
                if (moduleDirectories != null && tree instanceof SourceFile) {
                    moduleDirectories.addModuleDirectory((SourceFile) tree, jp);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
        @Nullable ModulesWithDependency moduleDirectories = acc instanceof ModulesWithDependency ? (ModulesWithDependency) acc : null;
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                boolean shouldInvert = Boolean.TRUE.equals(invertMarking);
                String dependencyGav = groupIdPattern + ":" + artifactIdPattern + (version == null ? "" : ":" + version);
                Set<JavaProject> modules = tree.getMarkers().findFirst(JavaProject.class)
                        .map(Collections::singleton)
                        .orElseGet(() -> moduleDirectories != null && tree instanceof SourceFile ?
                                moduleDirectories.enclosingModules(((SourceFile) tree).getSourcePath()) : emptySet());
                if (modules.isEmpty()) {
                    if (shouldInvert) {
                        return SearchResult.found(tree, "No module, so vacuously does not have dependency: " + dependencyGav);
                    }
                    return tree;
                }
                if (shouldInvert && disjoint(acc, modules)) {
                    return SearchResult.found(tree, "Module does not have dependency: " + dependencyGav);
                }
                if (!shouldInvert && acc.containsAll(modules)) {
                    return SearchResult.found(tree, "Module has dependency: " + dependencyGav);
                }
                return tree;
            }
        };
    }
}
