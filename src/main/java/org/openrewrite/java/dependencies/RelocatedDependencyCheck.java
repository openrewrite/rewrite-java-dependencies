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

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.oldgroupids.Migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

public class RelocatedDependencyCheck extends ScanningRecipe<RelocatedDependencyCheck.Accumulator> {
    @Override
    public String getDisplayName() {
        return "Find relocated dependencies";
    }

    @Override
    public String getDescription() {
        return "Find dependencies that have been relocated.";
    }

    @Value
    public static class Accumulator {
        Map<GroupArtifact, Relocation> migrations;
    }

    @Value
    static class GroupArtifact {
        String groupId;
        @Nullable
        String artifactId;
    }

    @Value
    static class Relocation {
        GroupArtifact to;
        String context;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Map<GroupArtifact, Relocation> migrations = new HashMap<>();
        try {
            ObjectReader objectReader = new CsvMapper().readerWithSchemaFor(Migration.class);
            MappingIterator<Migration> objectMappingIterator = objectReader
                    .readValues(RelocatedDependencyCheck.class.getResourceAsStream("/migrations.csv"));
            while (objectMappingIterator.hasNext()) {
                Migration def = objectMappingIterator.next();
                GroupArtifact oldGav = new GroupArtifact(def.getOldGroupId(), StringUtils.isBlank(def.getOldArtifactId())? null : def.getOldArtifactId());
                GroupArtifact newGav = new GroupArtifact(def.getNewGroupId(), StringUtils.isBlank(def.getNewArtifactId())? null : def.getNewArtifactId());
                migrations.put(oldGav, new Relocation(newGav, StringUtils.isBlank(def.getContext()) ? null : def.getContext()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Accumulator(migrations);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return null;
    }
}
