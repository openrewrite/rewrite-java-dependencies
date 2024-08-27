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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.csharp.dependencies.trait.PackageReference;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.table.DependenciesInUse;
import org.openrewrite.semver.Semver;

@Value
@EqualsAndHashCode(callSuper = false)
public class DependencyInsight extends Recipe {
    transient DependenciesInUse dependenciesInUse = new DependenciesInUse(this);

    @Option(displayName = "Artifact pattern",
            description = "Artifact ID glob pattern used to match dependencies.",
            example = "Microsoft*",
            required = false)
    @Nullable
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
    public String getDisplayName() {
        return "Dependency insight for C#";
    }

    @Override
    public String getDescription() {
        return "Finds dependencies in `*.csproj` and `packages.config`.";
    }

    @Override
    public Validated<Object> validate() {
        Validated<Object> v = super.validate();
        if (version != null) {
            v = v.and(Semver.validate(version, null));
        }
        return v;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PackageReference.Matcher().asVisitor((ref, ctx) -> {
            if (artifactIdPattern != null &&
                !StringUtils.matchesGlob(ref.getInclude(), artifactIdPattern)) {
                return ref.getTree();
            }

            if (version != null &&
                !Semver.validate(version, null).getValue().isValid(null, ref.getVersion())) {
                return ref.getTree();
            }

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
