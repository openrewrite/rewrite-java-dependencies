/*
 * Copyright 2021 the original author or authors.
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
public class DependencyListReport extends DataTable<DependencyListReport.Row> {
    public DependencyListReport(Recipe recipe) {
        super(recipe,
                "Dependency report",
                "Lists all Gradle and Maven dependencies");
    }

    @Value
    public static class Row {

        @Column(displayName = "Build tool",
                description = "The build tool used to manage dependencies (Gradle or Maven).")
        String buildTool;

        @Column(displayName = "Group id",
                description = "The Group ID of the Gradle project or Maven module requesting the dependency.")
        String groupId;

        @Column(displayName = "Artifact id",
                description = "The Artifact ID of the Gradle project or Maven module requesting the dependency.")
        String artifactId;

        @Column(displayName = "Version",
                description = "The version of Gradle project or Maven module requesting the dependency.")
        String version;

        @Column(displayName = "Dependency group id",
                description = "The Group ID of the dependency.")
        String dependencyGroupId;

        @Column(displayName = "Dependency artifact id",
                description = "The Artifact ID of the dependency.")
        String dependencyArtifactId;

        @Column(displayName = "Dependency version",
                description = "The version of the dependency.")
        String dependencyVersion;

        @Column(displayName = "Direct Dependency",
                description = "When `true` the project directly depends on the dependency. When `false` the project " +
                              "depends on the dependency transitively through at least one direct dependency.")
        boolean direct;
    }
}
