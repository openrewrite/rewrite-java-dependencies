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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class RepositoryAccessibilityReport extends DataTable<RepositoryAccessibilityReport.Row> {

    public RepositoryAccessibilityReport(Recipe recipe) {
        super(recipe,
            "Repository accessibility report",
            "Listing of all dependency repositories and whether they are accessible.");

    }

    @Value
    public static class Row {
        @Column(displayName = "Repository URI",
                description = "The URI of the repository")
        String uri;

        @Column(displayName = "Ping exception type",
                description = "Empty if the repository responded to a ping. Otherwise, the type of exception encountered " +
                              "when attempting to access the repository.")
        String pingExceptionType;

        @Column(displayName = "Ping error message",
                description = "Empty if the repository was accessible. Otherwise, the error message encountered when " +
                              "attempting to access the repository.")
        String pingExceptionMessage;

        @Column(displayName = "Ping HTTP code",
                description = "The HTTP response code returned by the repository. May be empty for non-HTTP repositories.")
        @Nullable
        Integer pingHttpCode;

        @Column(displayName = "Dependency resolution exception type",
                description = "Empty if ping failed, or if the repository successfully downloaded the specified dependency. Otherwise, the type of exception encountered " +
                              "when attempting to access the repository.")
        String dependencyResolveExceptionType;

        @Column(displayName = "Dependency resolution error message",
                description = "Empty if ping failed, or if the repository successfully downloaded the specified dependency. Otherwise, the error message encountered when " +
                              "attempting to access the repository.")
        String dependencyResolveExceptionMessage;
    }
}
