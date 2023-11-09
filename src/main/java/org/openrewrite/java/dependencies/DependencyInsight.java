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
