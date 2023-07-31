package org.openrewrite.java.dependencies.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParseAdvisoriesTest {

    @Test
    void parseAdvisories(@TempDir Path tmp) throws Exception {
        URI uri = ParseAdvisoriesTest.class.getResource("/advisories").toURI();
        Path output = tmp.resolve("advisories.csv");
        ParseAdvisories.parseAdvisories(Path.of(uri).toFile(), output.toFile());
        List<String> allLines = Files.readAllLines(output);
        assertThat(allLines).containsExactly("CVE-2023-34150,2023-07-05T09:30:20Z,\"Apache Any23 vulnerable to excessive memory usage\",\"org.apache.any23:apache-any23\",0,,MODERATE");
    }
}