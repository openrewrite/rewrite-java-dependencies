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
import java.util.List;

import static java.util.Collections.emptySet;

public class ParseAdvisories {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: ParseAdvisories <advisories-repo> <ecosystem> <advisories-csv>");
            System.exit(1);
        }
        File advisoriesRepo = new File(args[0]);
        if (!advisoriesRepo.isDirectory() || !advisoriesRepo.canRead()) {
            System.err.println("Advisories repo " + advisoriesRepo + " not readable");
            System.exit(1);
        }
        String ecosystem = args[1];
        File advisoriesCsv = new File(args[2]);
        if (!advisoriesCsv.createNewFile() && !advisoriesCsv.canWrite()) {
            System.err.println("Advisories CSV " + advisoriesCsv + " not writable");
            System.exit(1);
        }

        parseAdvisories(advisoriesRepo, ecosystem, advisoriesCsv);
    }

    static void parseAdvisories(File advisoriesRepoInput, String ecosystem, File advisoriesCsvOutput) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(advisoriesCsvOutput)) {
            Files.walkFileTree(advisoriesRepoInput.toPath(), emptySet(), 16, new MavenAdvisoriesVisitor(ecosystem, fos));
        }
    }

    private static final class MavenAdvisoriesVisitor extends SimpleFileVisitor<Path> {
        private final String ecosystem;
        private final FileOutputStream fos;
        private final ObjectMapper reader;
        private final ObjectWriter writer;

        public MavenAdvisoriesVisitor(String ecosystem, FileOutputStream fos) {
            this.ecosystem = ecosystem;
            this.fos = fos;
            this.reader = getObjectMapper();
            this.writer = getObjectWriter();
        }

        private Path current;

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            if (path.getFileName().toString().endsWith(".json")) {

                Path parent = path.getParent().getParent();
                if (current == null || !current.equals(parent)) {
                    current = parent;
                    System.out.println("Parsing " + current);
                }

                Advisory advisory = reader.readValue(path.toFile(), Advisory.class);
                for (Affected affected : advisory.getAffected()) {
                    if (affected.getPkg().getEcosystem().equalsIgnoreCase(ecosystem)
                            && affected.getRanges() != null
                            && !affected.getRanges().isEmpty()) {
                        Range range = affected.getRanges().iterator().next();
                        String cve = advisory.getAliases().isEmpty() ?
                                advisory.getId() :
                                advisory.getAliases().iterator().next();
                        List<String> cweIds = advisory.getDatabaseSpecific().getCweIds();
                        String cwes = cweIds == null || cweIds.isEmpty() ? null : String.join(";", cweIds);
                        Vulnerability vulnerability = new Vulnerability(
                                cve,
                                advisory.getPublished(),
                                advisory.getSummary(),
                                affected.getPkg().getName(),
                                range.getIntroduced(),
                                range.getFixed(),
                                Vulnerability.Severity.valueOf(advisory.getDatabaseSpecific().getSeverity()),
                                cwes
                        );
                        writer.writeValue(fos, vulnerability);
                    }
                }
            }
            return FileVisitResult.CONTINUE;
        }

        private static ObjectMapper getObjectMapper() {
            return new ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
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

}
