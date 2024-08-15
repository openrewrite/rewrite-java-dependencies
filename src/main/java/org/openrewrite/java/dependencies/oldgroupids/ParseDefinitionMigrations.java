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
package org.openrewrite.java.dependencies.oldgroupids;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ParseDefinitionMigrations {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: ParseDefinitionMigrations <oga-maven-plugin-repo> <migrations-csv>");
            System.exit(1);
        }
        File repo = new File(args[0]);
        if (!repo.isDirectory() || !repo.canRead()) {
            System.err.println("oga-maven-plugin repo " + repo + " not readable");
            System.exit(1);
        }
        File csv = new File(args[1]);
        if (!csv.createNewFile() && !csv.canWrite()) {
            System.err.println("CSV " + csv + " not writable");
            System.exit(1);
        }

        parseDefinitionMigrations(repo, csv);
    }

    static void parseDefinitionMigrations(File repo, File csv) throws IOException {
        ObjectMapper objectMapper = getObjectMapper();

        Path uc = repo.toPath().resolve("uc");
        File official = uc.resolve("og-definitions.json").toFile();
        File unofficial = uc.resolve("og-unofficial-definitions.json").toFile();

        List<DefinitionMigration> definitions = objectMapper.readValue(official, Definitions.class).getMigration();
        List<ProposedMigration> proposed = objectMapper.readValue(unofficial, UnofficialDefinitions.class).getMigration();

        List<Migration> migrations = new ArrayList<>(definitions.size() + proposed.size());
        for (DefinitionMigration d : definitions) {
            migrations.add(getMigration(d.getOldGav(), d.getNewGav(), d.getContext()));
        }
        for (ProposedMigration p : proposed) {
            migrations.add(getMigration(p.getOldGav(), p.getProposal().get(0), p.getContext()));
        }

        ObjectWriter objectWriter = getObjectWriter();
        objectWriter.writeValue(csv, migrations);
    }

    private static Migration getMigration(String oldGav1, String newGav1, String context) {
        String[] oldGav = oldGav1.split(":");
        String[] newGav = newGav1.split(":");
        return new Migration(
                oldGav[0], oldGav.length > 1 ? oldGav[1] : null,
                newGav[0], newGav.length > 1 ? newGav[1] : null,
                context);
    }

    private static ObjectMapper getObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new JavaTimeModule());
    }

    private static ObjectWriter getObjectWriter() {
        CsvFactory factory = new CsvFactory();
        factory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        CsvMapper csvMapper = CsvMapper.builder(factory)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
        return csvMapper.writer(csvMapper.schemaFor(Migration.class));
    }
}

/**
 * Mirrors <a href="https://github.com/jonathanlermitage/oga-maven-plugin/blob/oga-maven-plugin-1.8.1/src/main/java/biz/lermitage/oga/cfg/Definitions.kt#L8">Definitions.kt</a>
 */
@Data
class Definitions {
    List<DefinitionMigration> migration;
}

@Data
class DefinitionMigration {
    @JsonProperty("old")
    String oldGav;

    @JsonProperty("new")
    String newGav;

    @Nullable
    String context;
}

@Data
class UnofficialDefinitions {
    List<ProposedMigration> migration;
}

@Data
class ProposedMigration {
    @JsonProperty("old")
    String oldGav;

    List<String> proposal;

    @Nullable
    String context;
}
