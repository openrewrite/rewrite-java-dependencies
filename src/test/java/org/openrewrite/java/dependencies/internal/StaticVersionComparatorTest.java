package org.openrewrite.java.dependencies.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class StaticVersionComparatorTest {
    VersionParser vp = new VersionParser();
    StaticVersionComparator svc = new StaticVersionComparator();

    @Test
    void milestone() {
        assertThat(svc.compare(v("2.0.0"), v("1.0.0"))).isEqualTo(1);
        assertThat(svc.compare(v("1.0.0"), v("1.0.0-M1"))).isEqualTo(1);
        assertThat(svc.compare(v("1.0.0-M2"), v("1.0.0-M1"))).isEqualTo(1);
        assertThat(svc.compare(v("1.0.0-rc-1"), v("1.0.0-M1"))).isEqualTo(1);
    }

    Version v(String version) {
        return vp.transform(version);
    }
}
