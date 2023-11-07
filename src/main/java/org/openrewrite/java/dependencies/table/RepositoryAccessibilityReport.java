package org.openrewrite.java.dependencies.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

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

        @Column(displayName = "Error message",
                description = "Empty if the repository was accessible. Otherwise, the error message encountered when " +
                              "attempting to access the repository.")
        String errorMessage;
    }
}
