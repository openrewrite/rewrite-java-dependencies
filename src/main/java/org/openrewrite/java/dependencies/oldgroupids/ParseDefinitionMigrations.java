package org.openrewrite.java.dependencies.oldgroupids;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.openrewrite.internal.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

    private static void parseDefinitionMigrations(File repo, File csv) throws IOException {
        ObjectMapper objectMapper = getObjectMapper();

        Path uc = repo.toPath().resolve("uc");
        File official = uc.resolve("og-definitions.json").toFile();
        File unofficial = uc.resolve("og-unofficial-definitions.json").toFile();

        List<DefinitionMigration> definitions = objectMapper.readValue(official, Definitions.class).getMigration();
        List<ProposedMigration> proposed = objectMapper.readValue(unofficial, UnofficialDefinitions.class).getMigration();
        for (ProposedMigration p : proposed) {
            definitions.add(new DefinitionMigration(p.getOldGav(), p.getNewGav().get(0), p.getContext()));
        }

        ObjectWriter objectWriter = getObjectWriter();
        objectWriter.writeValue(csv, definitions);
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
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return csvMapper.writer(csvMapper.schemaFor(DefinitionMigration.class));
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
@AllArgsConstructor
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
    @JsonProperty("proposal")
    List<String> newGav;
    @Nullable
    String context;
}
