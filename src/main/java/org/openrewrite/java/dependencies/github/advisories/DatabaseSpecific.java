package org.openrewrite.java.dependencies.github.advisories;

import lombok.Value;
import org.openrewrite.internal.lang.Nullable;

@Value
public class DatabaseSpecific {
    @Nullable
    String severity;
}
