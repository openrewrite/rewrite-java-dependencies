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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveDependency extends Recipe { // TODO Extend ScanningRecipe instead
    @Option(displayName = "Group ID",
            description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "com.fasterxml.jackson*")
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
            example = "jackson-module*")
    String artifactId;

    @Option(displayName = "Unless using", description = "If a dependency is used in the code, do not remove it.", example = "org.aspectj.lang.*")
    @Nullable
    String unlessUsing;

    // Gradle only parameter
    @Option(displayName = "The dependency configuration", description = "The dependency configuration to remove from.", example = "api", required = false)
    @Nullable
    String configuration;

    // Maven only parameter
    @Option(displayName = "Scope",
            description = "Only remove dependencies if they are in this scope. If 'runtime', this will" +
                    "also remove dependencies in the 'compile' scope because 'compile' dependencies are part of the runtime dependency set",
            valid = {"compile", "test", "runtime", "provided"},
            example = "compile",
            required = false)
    @Nullable
    String scope;

    @Override
    public String getDisplayName() {
        return "Remove a Gradle or Maven dependency";
    }

    @Override
    public String getDescription() {
        return "For Gradle project, removes a single dependency from the dependencies section of the `build.gradle`.\n" +
                "For Maven project, removes a single dependency from the <dependencies> section of the pom.xml.";
    }

    org.openrewrite.gradle.@Nullable RemoveDependency removeGradleDependency;

    org.openrewrite.maven.@Nullable RemoveDependency removeMavenDependency;

    public RemoveDependency(
            String groupId,
            String artifactId,
            @Nullable String unlessUsing,
            @Nullable String configuration,
            @Nullable String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.unlessUsing = unlessUsing;
        this.configuration = configuration;
        this.scope = scope;
        removeGradleDependency = new org.openrewrite.gradle.RemoveDependency(groupId, artifactId, configuration);
        removeMavenDependency = new org.openrewrite.maven.RemoveDependency(groupId, artifactId, scope);
    }

    // TODO Provide a scanner that looks across java sources to see if `unlessUsing` is used
    // Optionally look for use in the correct scope and submodule, but for do-no-harm v1 can skip that for now


    // TODO remove this method, and instead override getVisitor, as the scanning condition is not evaluated for getRecipeList
    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(removeGradleDependency, removeMavenDependency);
    }

    // TODO override getVisitor that only calls out to removeGradleDependency or removeMavenDependency if the scanning condition is met
}
