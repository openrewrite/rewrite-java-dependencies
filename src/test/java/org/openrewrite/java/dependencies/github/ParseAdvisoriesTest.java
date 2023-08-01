/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.dependencies.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParseAdvisoriesTest {

    @Test
    void parseAdvisories(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("advisories.csv");
        ParseAdvisories.parseAdvisories(new File("src/test/advisories"), output.toFile());

        List<String> allLines = Files.readAllLines(output);
        assertThat(allLines).containsExactly("CVE-2023-34150,2023-07-05T09:30:20Z,\"Apache Any23 vulnerable to excessive memory usage\",\"org.apache.any23:apache-any23\",0,,MODERATE,CWE-20;CWE-400");
    }
}