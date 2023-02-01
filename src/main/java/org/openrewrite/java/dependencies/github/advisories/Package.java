package org.openrewrite.java.dependencies.github.advisories;

import lombok.Value;

@Value
public class Package {
    String ecosystem;
    String name;
}
