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
public class LicenseReport extends DataTable<LicenseReport.Row> {

    public LicenseReport(Recipe recipe) {
        super(recipe,
                "License report",
                "Contains a license report of third-party dependencies.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Group",
                description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`.")
        String groupId;

        @Column(displayName = "Artifact",
                description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`.")
        String artifactId;

        @Column(displayName = "Version",
                description = "The resolved version.")
        String version;

        @Column(displayName = "License name",
                description = "The actual name of the license as written in the third-party dependency.")
        String licenseName;

        @Column(displayName = "License type",
                description = "The license in use, based on the category of license inferred from the name.")
        String licenseType;
    }
}
