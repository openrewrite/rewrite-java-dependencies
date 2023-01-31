package org.openrewrite.java.dependencies.nvd.api;

import lombok.Value;

import java.util.List;

@Value
public class Description {
    String lang;
    String value;

    public static String getDescription(List<Description> descriptions) {
        for (Description description : descriptions) {
            if (description.getLang().equals("en")) {
                return description.getValue();
            }
        }
        throw new IllegalStateException("Expected to find an English description");
    }
}
