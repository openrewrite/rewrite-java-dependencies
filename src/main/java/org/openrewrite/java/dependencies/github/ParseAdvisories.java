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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.util.Collections.emptySet;

public class ParseAdvisories {
    public static void main(String[] args) throws IOException {
        File advisoriesRepo = new File(System.getProperty("user.home") + "/Projects/github/github/advisory-database/advisories");
        File advisoriesCsv = new File("src/main/resources/advisories.csv");
        parseAdvisories(advisoriesRepo, advisoriesCsv);
    }

    static void parseAdvisories(File advisoriesRepoInput, File advisoriesCsvOutput) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(advisoriesCsvOutput)) {
            Files.walkFileTree(advisoriesRepoInput.toPath(), emptySet(), 16, new PathSimpleFileVisitor(fos));
        }
    }
}

class PathSimpleFileVisitor extends SimpleFileVisitor<Path> {
    private final FileOutputStream fos;
    private final ObjectMapper reader;
    private final ObjectWriter writer;

    public PathSimpleFileVisitor(FileOutputStream fos) {
        this.fos = fos;
        this.reader = getObjectMapper();
        this.writer = getObjectWriter();
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        System.out.println("Parsing " + file);
        if (file.getFileName().toString().endsWith(".json")) {
            Advisory advisory = reader.readValue(file.toFile(), Advisory.class);
            for (Affected affected : advisory.getAffected()) {
                if (affected.getPkg().getEcosystem().equals("Maven")
                        && affected.getRanges() != null
                        && !affected.getRanges().isEmpty()) {
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
                    writer.writeValue(fos, vulnerability);
                }
            }
        }
        return FileVisitResult.CONTINUE;
    }

    private static ObjectMapper getObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new JavaTimeModule());
    }

    private static ObjectWriter getObjectWriter() {
        CsvFactory factory = new CsvFactory();
        factory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        CsvMapper csvMapper = (CsvMapper) CsvMapper.builder(factory)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()
                .registerModule(new JavaTimeModule());
        CsvSchema schema = csvMapper.schemaFor(Vulnerability.class);
        return csvMapper.writer(schema);
    }
}
