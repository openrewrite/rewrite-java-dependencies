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
import org.openrewrite.maven.table.DependenciesInUse;

@Value
@EqualsAndHashCode(callSuper = false)
public class DependencyInsight extends Recipe {
    // Populated by the other DependencyInsight visitors. Listed here so the saas knows to display this when the recipe runs
    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Override
    public String getDisplayName() {
        return "Dependency insight for Gradle and Maven";
    }

    @Override
    public String getDescription() {
        return "Finds dependencies, including transitive dependencies, in both Gradle and Maven projects. " +
               "Matches within all Gradle dependency configurations and Maven scopes.";
    }

    @Option(displayName = "Group pattern",
            description = "Group ID glob pattern used to match dependencies.",
            example = "com.fasterxml.jackson.module")
    String groupIdPattern;

    @Option(displayName = "Artifact pattern",
            description = "Artifact ID glob pattern used to match dependencies.",
            example = "jackson-module-*")
    String artifactIdPattern;

    @Option(displayName = "Version",
            description = "Match only dependencies with the specified version. " +
                          "Node-style [version selectors](https://docs.openrewrite.org/reference/dependency-version-selectors) may be used. " +
                          "All versions are searched by default.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> gdi = new org.openrewrite.gradle.search.DependencyInsight(groupIdPattern, artifactIdPattern,  version,null)
                    .getVisitor();
            final TreeVisitor<?, ExecutionContext> mdi = new org.openrewrite.maven.search.DependencyInsight(groupIdPattern, artifactIdPattern, null, version, false)
                    .getVisitor();
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if(!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if(gdi.isAcceptable(s, ctx)) {
                    s = (SourceFile) gdi.visitNonNull(s, ctx);
                }
                if(mdi.isAcceptable(s, ctx)) {
                    s = (SourceFile) mdi.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }
}
