package org.openrewrite.java.dependencies.nvd.api;

import lombok.Value;

@Value
public class Metrics {
    CvssMetricV31 cvssMetricV31;
}
