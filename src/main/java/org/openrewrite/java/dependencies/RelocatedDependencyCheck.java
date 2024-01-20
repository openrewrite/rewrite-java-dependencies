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
import org.openrewrite.*;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.oldgroupids.Migration;
import org.openrewrite.java.dependencies.table.RelocatedDependencyReport;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RelocatedDependencyCheck extends ScanningRecipe<RelocatedDependencyCheck.Accumulator> {
    transient RelocatedDependencyReport report = new RelocatedDependencyReport(this);

    @Override
    public String getDisplayName() {
        return "Find relocated dependencies";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Find Maven and Gradle dependencies and Maven plugins that have relocated to a new `groupId` or `artifactId`. " +
               "Relocation information comes from the [oga-maven-plugin](https://github.com/jonathanlermitage/oga-maven-plugin/) " +
               "maintained by Jonathan Lermitage, Filipe Roque and others. " +
               "This recipe makes no changes to any source file.";
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
        return TreeVisitor.noop();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final TreeVisitor<?, ExecutionContext> gradleVisitor = gradleVisitor(acc);
            private final TreeVisitor<?, ExecutionContext> mavenVisitor = mavenVisitor(acc);

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {

                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (gradleVisitor.isAcceptable(s, ctx)) {
                    s = (SourceFile) gradleVisitor.visitNonNull(s, ctx);
                } else if (mavenVisitor.isAcceptable(s, ctx)) {
                    s = (SourceFile) mavenVisitor.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> gradleVisitor(Accumulator acc) {
        MethodMatcher dependencyMatcher = new MethodMatcher("DependencyHandlerSpec *(..)");
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (dependencyMatcher.matches(mi)) {
                    List<Expression> methodArguments = mi.getArguments();
                    Expression firstMethodArgument = methodArguments.get(0);
                    if (firstMethodArgument instanceof J.Literal) {
                        J.Literal literal = (J.Literal) firstMethodArgument;
                        mi = searchInLiteral(literal, mi, ctx);
                    } else if (firstMethodArgument instanceof G.GString) {
                        G.GString gString = (G.GString) firstMethodArgument;
                        List<J> strings = gString.getStrings();
                        if (!strings.isEmpty() && strings.get(0) instanceof J.Literal) {
                            mi = searchInLiteral((J.Literal) strings.get(0), mi, ctx);
                        }
                    } else if (firstMethodArgument instanceof G.MapEntry) {
                        mi = searchInGMapEntry(methodArguments, mi, ctx);
                    }

                }
                return mi;
            }

            private J.MethodInvocation searchInLiteral(J.Literal literal, J.MethodInvocation mi, ExecutionContext ctx) {
                String gav = (String) literal.getValue();
                assert gav != null;
                String[] parts = gav.split(":");
                if (gav.length() >= 2) {
                    mi = maybeAddComment(acc, mi, parts[0], parts[1], ctx);
                }
                return mi;
            }

            private J.MethodInvocation searchInGMapEntry(List<Expression> methodArguments, J.MethodInvocation mi, ExecutionContext ctx) {
                String groupId = null;
                String artifactId = null;
                for (Expression e : methodArguments) {
                    if (!(e instanceof G.MapEntry)) {
                        continue;
                    }
                    G.MapEntry arg = (G.MapEntry) e;
                    if (!(arg.getKey() instanceof J.Literal)) {
                        continue;
                    }
                    J.Literal key = (J.Literal) arg.getKey();
                    Expression argValue = arg.getValue();
                    String valueValue = null;
                    if (argValue instanceof J.Literal) {
                        J.Literal value = (J.Literal) argValue;
                        if (value.getValue() instanceof String) {
                            valueValue = (String) value.getValue();
                        }
                    } else if (argValue instanceof J.Identifier) {
                        J.Identifier value = (J.Identifier) argValue;
                        valueValue = value.getSimpleName();
                    } else if (argValue instanceof G.GString) {
                        G.GString value = (G.GString) argValue;
                        List<J> strings = value.getStrings();
                        if (!strings.isEmpty() && strings.get(0) instanceof G.GString.Value) {
                            G.GString.Value versionGStringValue = (G.GString.Value) strings.get(0);
                            if (versionGStringValue.getTree() instanceof J.Identifier) {
                                valueValue = ((J.Identifier) versionGStringValue.getTree()).getSimpleName();
                            }
                        }
                    }
                    if (!(key.getValue() instanceof String)) {
                        continue;
                    }
                    String keyValue = (String) key.getValue();
                    if ("group".equals(keyValue)) {
                        groupId = valueValue;
                    } else if ("name".equals(keyValue)) {
                        artifactId = valueValue;
                    }
                }
                if (groupId != null) {
                    mi = maybeAddComment(acc, mi, groupId, artifactId, ctx);
                }
                return mi;
            }

        };
    }

    private TreeVisitor<?, ExecutionContext> mavenVisitor(Accumulator acc) {
        final XPathMatcher dependencyMatcher = new XPathMatcher("//dependencies/dependency");
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                tag = super.visitTag(tag, ctx);
                Optional<String> optionalGroupId = tag.getChildValue("groupId");
                Optional<String> optionalArtifactId = tag.getChildValue("artifactId");
                if (dependencyMatcher.matches(getCursor())) {
                    if (optionalGroupId.isPresent()) {
                        String groupId = optionalGroupId.get();
                        String artifactId = optionalArtifactId.orElse(null);
                        tag = maybeAddComment(acc, tag, groupId, artifactId, ctx);
                    }
                } else if (isPluginTag()) {
                    if (optionalArtifactId.isPresent()) {
                        String groupId = tag.getChildValue("groupId").orElse("org.apache.maven.plugins");
                        String artifactId = optionalArtifactId.get();
                        tag = maybeAddComment(acc, tag, groupId, artifactId, ctx);
                    }
                }
                return tag;
            }

        };
    }

    private <T extends Tree> T maybeAddComment(Accumulator acc, T tree, String groupId, @Nullable String artifactId, ExecutionContext ctx) {
        Relocation relocation = acc.getMigrations().get(new GroupArtifact(groupId, artifactId));
        if (relocation != null) {
            GroupArtifact relocatedGA = relocation.getTo();
            String commentText = String.format("Relocated to %s%s%s",
                    relocatedGA.getGroupId(),
                    Optional.ofNullable(relocatedGA.getArtifactId()).map(a -> ":" + a).orElse(""),
                    relocation.getContext() == null ? "" : " as per \"" + relocation.getContext() + "\"");
            report.insertRow(ctx, new RelocatedDependencyReport.Row(
                    groupId,
                    artifactId,
                    relocatedGA.getGroupId(),
                    relocatedGA.getArtifactId(),
                    relocation.getContext()));
            return SearchResult.found(tree, commentText);
        }
        if (artifactId == null) {
            return tree;
        }
        // Try again without artifactId
        return maybeAddComment(acc, tree, groupId, null, ctx);
    }
}


