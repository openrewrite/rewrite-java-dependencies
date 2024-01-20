/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.Accumulator;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.GroupArtifact;
import org.openrewrite.java.dependencies.RelocatedDependencyCheck.Relocation;
import org.openrewrite.test.RewriteTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelocatedDependencyCheckTest implements RewriteTest {
    @Test
    void initialValueParser() {
        Accumulator initialValue = new RelocatedDependencyCheck().getInitialValue(null);
        Map<GroupArtifact, Relocation> migrations = initialValue.getMigrations();
        assertThat(migrations).containsEntry(new GroupArtifact("commons-lang", "commons-lang"),
          new Relocation(new GroupArtifact("org.apache.commons", "commons-lang3"), null));
    }
}
