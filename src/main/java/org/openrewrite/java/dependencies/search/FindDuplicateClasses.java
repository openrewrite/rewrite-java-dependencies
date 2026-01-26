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
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.dependencies.table.DuplicateClassesReport;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindDuplicateClasses extends ScanningRecipe<FindDuplicateClasses.Accumulator> {

    transient DuplicateClassesReport report = new DuplicateClassesReport(this);

    @Override
    public String getDisplayName() {
        return "Find duplicate classes on the classpath";
    }

    @Override
    public String getDescription() {
        return "Detects classes that appear in multiple dependencies on the classpath. " +
               "This is similar to what the Maven duplicate-finder-maven-plugin does. " +
               "Duplicate classes can cause runtime issues when different versions " +
               "of the same class are loaded.";
    }

    public static class Accumulator {
        Set<ProjectSourceSet> seen = new HashSet<>();
    }

    @Value
    private static class ProjectSourceSet {
        String projectName;
        String sourceSetName;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                Optional<JavaSourceSet> maybeSourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
                if (!maybeSourceSet.isPresent()) {
                    return cu;
                }

                JavaSourceSet sourceSet = maybeSourceSet.get();
                String projectName = cu.getMarkers().findFirst(JavaProject.class)
                        .map(JavaProject::getProjectName)
                        .orElse("<unknown>");

                ProjectSourceSet pss = new ProjectSourceSet(projectName, sourceSet.getName());
                if (!acc.seen.add(pss)) {
                    return cu;
                }

                Map<String, List<JavaType.FullyQualified>> gavToTypes = sourceSet.getGavToTypes();
                if (gavToTypes == null || gavToTypes.isEmpty()) {
                    return cu;
                }

                // Invert the mapping: type name -> list of GAVs containing that type
                Map<String, List<String>> typeToGavs = new HashMap<>();
                for (Map.Entry<String, List<JavaType.FullyQualified>> entry : gavToTypes.entrySet()) {
                    String gav = entry.getKey();
                    for (JavaType.FullyQualified type : entry.getValue()) {
                        typeToGavs.computeIfAbsent(type.getFullyQualifiedName(), k -> new ArrayList<>()).add(gav);
                    }
                }

                // Report duplicates (types appearing in more than one GAV)
                for (Map.Entry<String, List<String>> entry : typeToGavs.entrySet()) {
                    List<String> gavs = entry.getValue();
                    if (gavs.size() > 1) {
                        String typeName = entry.getKey();
                        // Skip module-info and package-info files (multi-release JAR false positives)
                        if (typeName.contains("module-info") || typeName.endsWith("package-info")) {
                            continue;
                        }
                        String additionalDeps = gavs.size() > 2
                                ? gavs.subList(2, gavs.size()).stream().collect(joining(", "))
                                : "";
                        report.insertRow(ctx, new DuplicateClassesReport.Row(
                                projectName,
                                sourceSet.getName(),
                                typeName,
                                gavs.get(0),
                                gavs.get(1),
                                additionalDeps
                        ));
                    }
                }

                return cu;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        return emptyList();
    }
}
