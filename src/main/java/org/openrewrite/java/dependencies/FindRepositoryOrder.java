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
package org.openrewrite.java.dependencies;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindRepositoryOrder extends Recipe {

    String displayName = "Maven repository order";

    String description = "Determine the order in which dependencies will be resolved for each `pom.xml` or " +
                         "`build.gradle` based on its defined repositories and effective settings.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {

            final TreeVisitor<?, ExecutionContext> mavenFindRepositoryOrder =
                    new org.openrewrite.maven.search.FindRepositoryOrder().getVisitor();
            final TreeVisitor<?, ExecutionContext> gradleFindRepositoryOrder =
                    new org.openrewrite.gradle.search.FindRepositoryOrder().getVisitor();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (mavenFindRepositoryOrder.isAcceptable(s, ctx)) {
                    return mavenFindRepositoryOrder.visitNonNull(tree, ctx);
                }
                if (gradleFindRepositoryOrder.isAcceptable(s, ctx)) {
                    return gradleFindRepositoryOrder.visitNonNull(tree, ctx);
                }
                return s;
            }
        };
    }
}
