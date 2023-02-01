package org.openrewrite.java.dependencies.github;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.openrewrite.java.dependencies.Vulnerability;
import org.openrewrite.java.dependencies.github.advisories.Advisory;
import org.openrewrite.java.dependencies.github.advisories.Affected;
import org.openrewrite.java.dependencies.github.advisories.Range;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.util.Collections.emptySet;

public class ParseAdvisories {
    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new JavaTimeModule());

        CsvFactory factory = new CsvFactory();
        factory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        CsvMapper csvMapper = (CsvMapper) CsvMapper.builder(factory)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()
                .registerModule(new JavaTimeModule());
        CsvSchema schema = csvMapper.schemaFor(Vulnerability.class);
        ObjectWriter vWriter = csvMapper.writer(schema);

        try (FileOutputStream f = new FileOutputStream("src/main/resources/advisories.csv")) {
            Files.walkFileTree(new File(System.getProperty("user.home") + "/Projects/github/github/advisory-database/advisories").toPath(), emptySet(), 16, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    System.out.println("Parsing " + file);
                    if (file.getFileName().toString().endsWith(".json")) {
                        Advisory advisory = mapper.readValue(file.toFile(), Advisory.class);
                        for (Affected affected : advisory.getAffected()) {
                            if (affected.getPkg().getEcosystem().equals("Maven") &&
                                affected.getRanges() != null && !affected.getRanges().isEmpty()) {
                                Range range = affected.getRanges().iterator().next();
                                Vulnerability vulnerability = new Vulnerability(
                                        advisory.getAliases().isEmpty() ?
                                                advisory.getId() :
                                                advisory.getAliases().iterator().next(),
                                        advisory.getPublished(),
                                        advisory.getSummary(),
                                        affected.getPkg().getName(),
                                        range.getIntroduced(),
                                        range.getFixed(),
                                        Vulnerability.Severity.valueOf(advisory.getDatabaseSpecific().getSeverity())
                                );
                                vWriter.writeValue(f, vulnerability);
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
