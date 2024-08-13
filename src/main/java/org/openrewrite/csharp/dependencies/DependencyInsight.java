/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.csharp.dependencies;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.csharp.dependencies.trait.PackageReference;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.table.DependenciesInUse;

public class DependencyInsight extends Recipe {
    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Override
    public String getDisplayName() {
        return "Dependency insight for C#";
    }

    @Override
    public String getDescription() {
        return "Finds dependencies in `*.csproj` and `packages.config`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PackageReference.Matcher().asVisitor((ref, ctx) -> {
            dependenciesInUse.insertRow(ctx, new DependenciesInUse.Row(
                    null,
                    null,
                    null,
                    ref.getInclude(),
                    ref.getVersion(),
                    null,
                    null,
                    0
            ));
            return SearchResult.found(ref.getTree(),
                    String.format("%s:%s", ref.getInclude(), ref.getVersion()));
        });
    }
}
