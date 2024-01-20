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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.oldgroupids.Migration;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class RelocatedDependencyCheck extends ScanningRecipe<RelocatedDependencyCheck.Accumulator> {
    @Override
    public String getDisplayName() {
        return "Find relocated dependencies";
    }

    @Override
    public String getDescription() {
        return "Find dependencies that have been relocated.";
        // TODO credit https://github.com/jonathanlermitage/oga-maven-plugin
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
        return Preconditions.or(new IsBuildGradle<>(), new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable Xml visit(@Nullable Tree tree, ExecutionContext executionContext) {
                return null; // Any Maven pom.xml file
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> gradleVisitor = gradleVisitor(acc);
        TreeVisitor<?, ExecutionContext> mavenVisitor = mavenVisitor(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
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
                    List<Expression> depArgs = method.getArguments();
                    if (depArgs.get(0) instanceof J.Literal) {
                        String gav = (String) ((J.Literal) depArgs.get(0)).getValue();
                        assert gav != null;
                        String[] parts = gav.split(":");
                        if (gav.length() >= 2) {
                            String groupId = parts[0];
                            String artifactId = parts[1];
                            mi = maybeAddComment(acc, mi, groupId, artifactId);
                        }
                    } else {
                        System.out.println("depArgs = " + depArgs);
                    }
                }
                return mi;
            }
        };
    }

    private static TreeVisitor<?, ExecutionContext> mavenVisitor(Accumulator acc) {
        final XPathMatcher dependencyMatcher = new XPathMatcher("/project/*/dependencies/dependency");
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                tag = super.visitTag(tag, ctx);
                Optional<String> optionalGroupId = tag.getChildValue("groupId");
                Optional<String> optionalArtifactId = tag.getChildValue("artifactId");
                if (isDependencyTag() || dependencyMatcher.matches(getCursor())) {
                    if (optionalGroupId.isPresent()) {
                        String groupId = optionalGroupId.get();
                        String artifactId = optionalArtifactId.orElse(null);
                        tag = maybeAddComment(acc, tag, groupId, artifactId);
                        tag = maybeAddComment(acc, tag, groupId, null);
                    }
                } else if (isPluginTag()) {
                    if (optionalArtifactId.isPresent()) {
                        String groupId = tag.getChildValue("groupId").orElse("org.apache.maven.plugins");
                        String artifactId = optionalArtifactId.get();
                        tag = maybeAddComment(acc, tag, groupId, artifactId);
                        tag = maybeAddComment(acc, tag, groupId, null);
                    }
                }
                return tag;
            }

        };
    }

    private static <T extends Tree> T maybeAddComment(Accumulator acc, T tree, String groupId, @Nullable String artifactId) {
        Relocation relocation = acc.getMigrations().get(new GroupArtifact(groupId, artifactId));
        if (relocation != null) {
            String commentText = String.format("Relocated to %s%s%s",
                    relocation.getTo().getGroupId(),
                    Optional.ofNullable(relocation.getTo().getArtifactId()).map(a -> ":" + a).orElse(""),
                    relocation.getContext() == null ? "" : " as per \"" + relocation.getContext() + "\"");
            return SearchResult.found(tree, commentText);
        }
        return tree;
    }

    // TODO switch to the provided one in https://github.com/openrewrite/rewrite/pull/3933 when ready
    static class IsBuildGradle<P> extends TreeVisitor<Tree, P> {
        @Override
        public Tree visit(@Nullable Tree tree, P p) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                if (matches(cu.getSourcePath())) {
                    return SearchResult.found(cu);
                }
            }
            return super.visit(tree, p);
        }

        public static boolean matches(Path sourcePath) {
            return (sourcePath.toString().endsWith(".gradle") ||
                    sourcePath.toString().endsWith(".gradle.kts")) &&
                   !(sourcePath.toString().endsWith("settings.gradle") ||
                     sourcePath.toString().endsWith("settings.gradle.kts"));
        }
    }
}


