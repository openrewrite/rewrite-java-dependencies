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
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.oldgroupids.Migration;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

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
        try {
            MappingIterator<Migration> objectMappingIterator = new CsvMapper()
                    .readerWithSchemaFor(Migration.class)
                    .readValues(RelocatedDependencyCheck.class.getResourceAsStream("/migrations.csv"));
            Map<GroupArtifact, Relocation> migrations = new HashMap<>();
            while (objectMappingIterator.hasNext()) {
                Migration def = objectMappingIterator.next();
                GroupArtifact oldGav = new GroupArtifact(def.getOldGroupId(), StringUtils.isBlank(def.getOldArtifactId()) ? null : def.getOldArtifactId());
                GroupArtifact newGav = new GroupArtifact(def.getNewGroupId(), StringUtils.isBlank(def.getNewArtifactId()) ? null : def.getNewArtifactId());
                migrations.put(oldGav, new Relocation(newGav, StringUtils.isBlank(def.getContext()) ? null : def.getContext()));
            }
            return new Accumulator(migrations);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable Xml visit(@Nullable Tree tree, ExecutionContext executionContext) {
                return null; // Any Maven pom.xml file
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return mavenVisitor(acc);
    }

    private static TreeVisitor<?, ExecutionContext> mavenVisitor(Accumulator acc) {
        final XPathMatcher dependencyMatcher = new XPathMatcher("/project/*/dependencies/dependency");
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                tag = super.visitTag(tag, executionContext);
                Optional<String> optionalGroupId = tag.getChildValue("groupId");
                Optional<String> optionalArtifactId = tag.getChildValue("artifactId");
                if (isDependencyTag() || dependencyMatcher.matches(getCursor())) {
                    if (optionalGroupId.isPresent()) {
                        String groupId = optionalGroupId.get();
                        String artifactId = optionalArtifactId.orElse(null);
                        tag = maybeAddComment(tag, groupId, artifactId);
                        tag = maybeAddComment(tag, groupId, null);
                    }
                } else if (isPluginTag()) {
                    if (optionalArtifactId.isPresent()) {
                        String groupId = tag.getChildValue("groupId").orElse("org.apache.maven.plugins");
                        String artifactId = optionalArtifactId.get();
                        tag = maybeAddComment(tag, groupId, artifactId);
                        tag = maybeAddComment(tag, groupId, null);
                    }
                }
                return tag;
            }

            private Xml.Tag maybeAddComment(Xml.Tag tag, String groupId, @Nullable String artifactId) {
                Relocation relocation = acc.getMigrations().get(new GroupArtifact(groupId, artifactId));
                if (relocation != null) {
                    String commentText = String.format("Relocated to %s%s%s",
                            relocation.getTo().getGroupId(),
                            Optional.ofNullable(relocation.getTo().getArtifactId())
                                    .map(a -> ":" + a)
                                    .orElse(""),
                            relocation.getContext() == null ? "" : " as per \"" + relocation.getContext() + "\"");
                    List<Content> contents = new ArrayList<>(tag.getContent());
                    boolean containsComment = contents.stream()
                            .anyMatch((c) -> c instanceof Xml.Comment && commentText.equals(((Xml.Comment) c).getText()));
                    if (!containsComment) {
                        int insertPos = 0;
                        Xml.Comment customComment = new Xml.Comment(Tree.randomId(), contents.get(insertPos).getPrefix(), Markers.EMPTY, commentText);
                        contents.add(insertPos, customComment);
                        return tag.withContent(contents);
                    }
                }
                return tag;
            }
        };
    }
}
