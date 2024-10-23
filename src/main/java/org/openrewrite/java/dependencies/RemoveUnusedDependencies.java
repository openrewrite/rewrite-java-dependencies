package org.openrewrite.java.dependencies;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveUnusedDependencies extends ScanningRecipe<RemoveUnusedDependencies.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Remove unused dependencies";
    }

    @Override
    public String getDescription() {
        return "Scans through source code collecting references to types and methods, removing any dependencies that " +
               "are not used from Maven or Gradle build files.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if (tree instanceof JavaSourceFile) {
                    acc.recordTypesInUse((JavaSourceFile) tree);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @SuppressWarnings("NullableProblems")
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                JavaProject javaProject = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (javaProject == null) {
                    return tree;
                }
                MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                if (mrr != null) {
                    List<ResolvedDependency> dependencies = mrr.getDependencies().get(Scope.Test);
                    for (ResolvedDependency dependency : dependencies) {
                        GroupArtifact ga = dependency.getGav().asGroupArtifact();
                        if (!acc.isInUse(javaProject, ga)) {
                            tree = new org.openrewrite.maven.RemoveDependency(ga.getGroupId(), ga.getArtifactId(), null)
                                    .getVisitor()
                                    .visitNonNull(tree, ctx);
                        }
                    }

                }
                GradleProject gp = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
                if (gp != null) {
                    // TODO
                    // Edge case to remember: Freestanding Gradle scripts have a GradleProject marker from only the project they are located in
                    // But such a "dependencies.gradle" script could be included in multiple projects, so we need to be careful
                }

                return tree;
            }
        };
    }

    public static class Accumulator {
        private final Map<JavaProject, Set<String>> projectToTypesInUse = new HashMap<>();
        private final Map<String, GroupArtifact> typeFqnToGA = new HashMap<>();

        public boolean isInUse(JavaProject project, GroupArtifact ga) {
            Set<String> typesInUse = projectToTypesInUse.get(project);
            for (String type : typesInUse) {
                if (ga.equals(typeFqnToGA.get(type))) {
                    return true;
                }
            }
            return false;
        }

        public void recordTypesInUse(JavaSourceFile cu) {
            TypesInUse types = cu.getTypesInUse();
            JavaProject javaProject = cu.getMarkers().findFirst(JavaProject.class).orElse(null);
            JavaSourceSet javaSourceSet = cu.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
            if (javaSourceSet == null || javaProject == null) {
                return;
            }
            recordTypesInUse(types, javaProject, javaSourceSet);
        }

        public void recordTypesInUse(TypesInUse types, JavaProject javaProject, JavaSourceSet jss) {
            projectToTypesInUse.compute(javaProject, (k, v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                // This is probably not sufficient to get everything that could possibly be in use
                types.getTypesInUse().stream()
                        .filter(JavaType.FullyQualified.class::isInstance)
                        .map(JavaType.FullyQualified.class::cast)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .forEach(v::add);
                return v;
            });
            // This isn't great for performance, should be improved before it can be used in a real-world scenario
            for (Map.Entry<String, List<JavaType.FullyQualified>> gavToTypes : jss.getGavToTypes().entrySet()) {
                String[] gav = gavToTypes.getKey().split(":");
                String group = gav[0];
                String artifact = gav[1];
                GroupArtifact ga = new GroupArtifact(group, artifact);
                for (JavaType.FullyQualified type : gavToTypes.getValue()) {
                    String fqn = type.getFullyQualifiedName();
                    typeFqnToGA.put(fqn, ga);
                }
            }
        }
    }
}
