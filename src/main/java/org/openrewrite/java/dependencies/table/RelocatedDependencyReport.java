package org.openrewrite.java.dependencies.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;

@JsonIgnoreType
public class RelocatedDependencyReport extends DataTable<RelocatedDependencyReport.Row> {
    public RelocatedDependencyReport(Recipe recipe) {
        super(recipe,
                "Relocated dependencies",
                "A list of dependencies in use that have relocated.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Dependency group id",
                description = "The Group ID of the dependency in use.")
        String dependencyGroupId;
        @Column(displayName = "Dependency artifact id",
                description = "The Artifact ID of the dependency in use.")
        @Nullable
        String dependencyArtifactId;

        @Column(displayName = "Relocated dependency group id",
                description = "The Group ID of the relocated dependency.")
        String relocatedGroupId;
        @Column(displayName = "Relocated ependency artifact id",
                description = "The Artifact ID of the relocated dependency.")
        @Nullable
        String relocatedArtifactId;

        @Column(displayName = "Context",
                description = "Context for the relocation, if any.")
        @Nullable
        String context;
    }
}
