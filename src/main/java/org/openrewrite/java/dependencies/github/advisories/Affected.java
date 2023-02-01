package org.openrewrite.java.dependencies.github.advisories;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class Affected {

    @JsonProperty("package")
    Package pkg;

    List<Range> ranges;
}
