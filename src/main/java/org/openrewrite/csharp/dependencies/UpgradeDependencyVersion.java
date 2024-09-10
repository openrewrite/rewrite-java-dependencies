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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.csharp.dependencies.trait.PackageReference;
import org.openrewrite.internal.StringUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpgradeDependencyVersion extends Recipe {
    @Option(displayName = "Package pattern",
            description = "Package glob pattern used to match dependencies.",
            example = "Microsoft*")
    String packagePattern;

    @Option(displayName = "New version",
            description = "An exact version number.",
            example = "12.3")
    String newVersion;

    @Override
    public String getDisplayName() {
        return "Upgrade C# dependency versions";
    }

    @Override
    public String getDescription() {
        return "Upgrades dependencies in `*.csproj` and `packages.config`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PackageReference.Matcher().asVisitor((ref, ctx) -> {
            if (StringUtils.matchesGlob(ref.getInclude(), packagePattern)) {
                return ref.withVersion(newVersion);
            }
            return ref.getTree();
        });
    }
}
