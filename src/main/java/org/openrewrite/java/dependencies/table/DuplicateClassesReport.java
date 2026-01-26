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
package org.openrewrite.java.dependencies.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class DuplicateClassesReport extends DataTable<DuplicateClassesReport.Row> {
    public DuplicateClassesReport(Recipe recipe) {
        super(recipe,
                "Duplicate classes report",
                "Lists classes that appear in multiple dependencies on the classpath");
    }

    @Value
    public static class Row {

        @Column(displayName = "Project name",
                description = "The project containing the duplicate.")
        String projectName;

        @Column(displayName = "Source set",
                description = "The source set containing the duplicate (e.g., main, test).")
        String sourceSet;

        @Column(displayName = "Type name",
                description = "The fully qualified name of the duplicate class.")
        String typeName;

        @Column(displayName = "Dependency 1",
                description = "The first dependency containing the class (group:artifact:version).")
        String dependency1;

        @Column(displayName = "Dependency 2",
                description = "The second dependency containing the class (group:artifact:version).")
        String dependency2;

        @Column(displayName = "Additional dependencies",
                description = "Any additional dependencies beyond the first two that also contain this class, comma-separated.")
        String additionalDependencies;
    }
}
