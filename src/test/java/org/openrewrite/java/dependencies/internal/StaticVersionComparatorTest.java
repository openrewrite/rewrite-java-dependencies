/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.dependencies.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class StaticVersionComparatorTest {
    VersionParser vp = new VersionParser();
    StaticVersionComparator svc = new StaticVersionComparator();

    @Test
    void milestone() {
        assertThat(svc.compare(v("2.0.0"), v("1.0.0"))).isOne();
        assertThat(svc.compare(v("1.0.0"), v("1.0.0-M1"))).isOne();
        assertThat(svc.compare(v("1.0.0-M2"), v("1.0.0-M1"))).isOne();
        assertThat(svc.compare(v("1.0.0-rc-1"), v("1.0.0-M1"))).isOne();
    }

    Version v(String version) {
        return vp.transform(version);
    }
}
