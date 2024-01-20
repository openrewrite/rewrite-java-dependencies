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
package org.openrewrite.java.dependencies.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class GradleDependencyConfigurationErrors extends DataTable<GradleDependencyConfigurationErrors.Row> {
    public GradleDependencyConfigurationErrors(Recipe recipe) {
        super(recipe, "Gradle dependency configuration errors",
                "Records Gradle dependency configurations which failed to resolve during parsing. " +
                "Partial success/failure is common, a failure in this list does not mean that every dependency failed to resolve.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Project path",
                description = "The path of the project which contains the dependency configuration.")
        String projectPath;

        @Column(displayName = "Configuration name",
                description = "The name of the dependency configuration which failed to resolve.")
        String configurationName;

        @Column(displayName = "Exception type",
                description = "The type of exception encountered when attempting to resolve the dependency configuration.")
        String exceptionType;

        @Column(displayName = "Error message",
                description = "The error message encountered when attempting to resolve the dependency configuration.")
        String exceptionMessage;
    }
}
