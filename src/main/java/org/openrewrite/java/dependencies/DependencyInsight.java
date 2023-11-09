package org.openrewrite.java.dependencies;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.tree.Scope;

@Value
@EqualsAndHashCode(callSuper = true)
public class DependencyInsight extends Recipe {
    @Override
    public String getDisplayName() {
        return "Dependency Insight Gradle or Maven";
    }

    @Override
    public String getDescription() {
        return "Finds dependencies, including transitive dependencies, in both Gradle and Maven projects. " +
               "Matches within all Gradle dependency configurations and maven scopes.";
    }

    @Option(displayName = "Group pattern",
            description = "Group glob pattern used to match dependencies.",
            example = "com.fasterxml.jackson.module")
    String groupIdPattern;

    @Option(displayName = "Artifact pattern",
            description = "Artifact glob pattern used to match dependencies.",
            example = "jackson-module-*")
    String artifactIdPattern;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gdi = new org.openrewrite.gradle.search.DependencyInsight(groupIdPattern, artifactIdPattern, null)
                    .getVisitor();
            final TreeVisitor<?, ExecutionContext> mdi = new org.openrewrite.maven.search.DependencyInsight(groupIdPattern, artifactIdPattern, Scope.Test.name(), false)
                    .getVisitor();
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if(!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if(gdi.isAcceptable(s, ctx)) {
                    s = (SourceFile) gdi.visitNonNull(s, ctx);
                } else if(mdi.isAcceptable(s, ctx)) {
                    s = (SourceFile) mdi.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }
}
