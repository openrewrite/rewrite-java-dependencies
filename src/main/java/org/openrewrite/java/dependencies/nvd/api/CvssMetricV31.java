package org.openrewrite.java.dependencies.nvd.api;

import lombok.Value;

@Value
public class CvssMetricV31 {
    CvssData cvssData;
}
