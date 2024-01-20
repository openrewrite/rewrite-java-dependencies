package org.openrewrite.java.dependencies.oldgroupids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ParseDefinitionMigrationsTest {
    @Test
    void parseDefinitionMigrations(@TempDir Path tempDir) throws IOException {
        ParseDefinitionMigrations parseDefinitionMigrations = new ParseDefinitionMigrations();
        Path csv = tempDir.resolve("migrations.csv");
        parseDefinitionMigrations.parseDefinitionMigrations(new File("src/test/resources"), csv.toFile());
        assertThat(csv).hasContent("""
          acegisecurity,,org.acegisecurity,,
          activation,activation,javax.activation,activation,
          com.jcraft,jsch,com.github.mwiede,jsch,"See https://www.matez.de/index.php/2020/06/22/the-future-of-jsch-without-ssh-rsa/"
          """);
    }
}