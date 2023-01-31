package org.openrewrite.java.dependencies.nvd.api;

import lombok.Value;

import java.util.List;

@Value
public class Weakness {
    List<Description> descriptions;
}
