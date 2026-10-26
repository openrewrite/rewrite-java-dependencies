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
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.trait.GradleDependency;
import org.openrewrite.gradle.trait.VersionCatalog;
import org.openrewrite.gradle.trait.VersionCatalogLibrary;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.dependencies.oldgroupids.Migration;
import org.openrewrite.java.dependencies.table.RelocatedDependencyReport;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.ChangePluginGroupIdAndArtifactId;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = false)
@Value
public class RelocatedDependencyCheck extends ScanningRecipe<RelocatedDependencyCheck.Accumulator> {
    private static final XPathMatcher DEPENDENCY_MATCHER = new XPathMatcher("//dependencies/dependency");
    private static final XPathMatcher ANNOTATION_PROCESSOR_PATH_MATCHER = new XPathMatcher("//annotationProcessorPaths/path");
    private static final XPathMatcher PARENT_MATCHER = new XPathMatcher("/project/parent");
    private static final XPathMatcher EXTENSION_MATCHER = new XPathMatcher("/project/build/extensions/extension");

    transient RelocatedDependencyReport report = new RelocatedDependencyReport(this);

    @Option(displayName = "Change dependencies",
            description = "Whether to change dependencies and Maven plugins to their relocated groupId and artifactId. " +
                          "A declaration is only changed when the relocated artifact publishes the version in use, " +
                          "so that the build resolves the same code under its new coordinates. " +
                          "Otherwise it is marked, as are `<parent>` and `<extension>` declarations.",
            required = false)
    @Nullable
    Boolean changeDependencies;

    String displayName = "Find relocated dependencies";

    String description = "Find Maven and Gradle dependencies, version catalog entries, Maven plugins, parents and extensions " +
               "that have relocated to a new `groupId` or `artifactId`. " +
               "Relocation information comes from the [oga-maven-plugin](https://github.com/jonathanlermitage/oga-maven-plugin/) " +
               "maintained by Jonathan Lermitage, Filipe Roque and others.\n\n" +
               "This recipe makes no changes to any source file by default. Add `changeDependencies=true` to change dependencies " +
               "whose relocated artifact publishes the same version, " +
               "but note that you might need to run additional recipes to update imports and adopt other breaking changes.";

    @Value
    public static class Accumulator {
        Map<GroupArtifact, Relocation> migrations;

        /**
         * Where each declaration of a relocated dependency or plugin can move to, `null` for one that can't.
         * All declarations of it must agree before any of them move.
         */
        Map<Usage, Set<@Nullable GroupArtifact>> targets = new HashMap<>();

        Map<Usage, Set<@Nullable GroupArtifact>> versionlessTargets = new HashMap<>();

        /**
         * A Maven declaration without a version moves along with the `<dependencyManagement>` entry
         * that manages it in this repository, so only one managed elsewhere has a say.
         */
        Set<GroupArtifact> managedInRepository = new HashSet<>();

        @Nullable
        GroupArtifact changeTo(Usage usage) {
            Set<@Nullable GroupArtifact> candidates = new HashSet<>(targets.getOrDefault(usage, Collections.emptySet()));
            if (!managedInRepository.contains(usage.getFrom())) {
                candidates.addAll(versionlessTargets.getOrDefault(usage, Collections.emptySet()));
            }
            return candidates.size() == 1 ? candidates.iterator().next() : null;
        }
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

        @Nullable
        String context;

        boolean official;
    }

    enum Kind {
        DEPENDENCY,
        PLUGIN,
        PARENT,
        EXTENSION
    }

    @Value
    static class Usage {
        Kind kind;
        GroupArtifact from;
    }

    @Value
    static class Hop {
        GroupArtifact to;
        Relocation relocation;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        try {
            Set<GroupArtifact> denied = new HashSet<>();
            CsvMapper csvMapper = new CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY);
            try (InputStream denylist = RelocatedDependencyCheck.class.getResourceAsStream("/migrations-denylist.csv")) {
                MappingIterator<String[]> deniedRows = csvMapper.readerFor(String[].class).readValues(denylist);
                while (deniedRows.hasNext()) {
                    String[] row = deniedRows.next();
                    denied.add(groupArtifact(row[0], row[1]));
                }
            }

            Map<GroupArtifact, Relocation> migrations = new HashMap<>();
            try (InputStream csv = RelocatedDependencyCheck.class.getResourceAsStream("/migrations.csv")) {
                MappingIterator<Migration> rows = csvMapper.readerWithSchemaFor(Migration.class).readValues(csv);
                while (rows.hasNext()) {
                    Migration def = rows.next();
                    GroupArtifact oldGa = groupArtifact(def.getOldGroupId(), def.getOldArtifactId());
                    if (!denied.contains(oldGa)) {
                        migrations.put(oldGa, new Relocation(
                                groupArtifact(def.getNewGroupId(), def.getNewArtifactId()),
                                StringUtils.isBlank(def.getContext()) ? null : def.getContext(),
                                !Migration.UNOFFICIAL.equals(def.getSource())));
                    }
                }
            }
            return new Accumulator(migrations);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GroupArtifact groupArtifact(String groupId, @Nullable String artifactId) {
        return new GroupArtifact(groupId, StringUtils.isBlank(artifactId) ? null : artifactId);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        if (!Boolean.TRUE.equals(changeDependencies)) {
            return TreeVisitor.noop();
        }
        return new DeclarationVisitor(acc) {
            @Override
            <T extends Tree> T visitDeclaration(Declaration declaration, List<Hop> chain, T tree, ExecutionContext ctx) {
                if (declaration.getKind() != Kind.DEPENDENCY && declaration.getKind() != Kind.PLUGIN) {
                    return tree;
                }
                Usage usage = new Usage(declaration.getKind(), declaration.getFrom());
                Hop hop = furthestAvailable(declaration, chain);
                if (declaration.isManaged()) {
                    acc.getManagedInRepository().add(declaration.getFrom());
                }
                (declaration.isVersionless() ? acc.getVersionlessTargets() : acc.getTargets())
                        .computeIfAbsent(usage, k -> new HashSet<>())
                        .add(hop == null ? null : hop.getTo());
                return tree;
            }

            private @Nullable Hop furthestAvailable(Declaration declaration, List<Hop> chain) {
                Hop furthest = null;
                for (Hop hop : chain) {
                    // Unofficial rows propose a successor or a fork rather than the same artifact under new coordinates
                    if (!hop.getRelocation().isOfficial()) {
                        break;
                    }
                    if (declaration.isAvailableAt(hop.getTo())) {
                        furthest = hop;
                    }
                }
                return furthest;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new DeclarationVisitor(acc) {
            private final Map<Usage, GroupArtifact> changes = new LinkedHashMap<>();

            @Override
            <T extends Tree> T visitDeclaration(Declaration declaration, List<Hop> chain, T tree, ExecutionContext ctx) {
                Hop last = chain.get(chain.size() - 1);
                report.insertRow(ctx, new RelocatedDependencyReport.Row(
                        declaration.getFrom().getGroupId(), declaration.getFrom().getArtifactId(),
                        last.getTo().getGroupId(), last.getTo().getArtifactId(),
                        context(chain)));

                Usage usage = new Usage(declaration.getKind(), declaration.getFrom());
                GroupArtifact target = Boolean.TRUE.equals(changeDependencies) ? acc.changeTo(usage) : null;
                if (target == null) {
                    return SearchResult.found(tree, relocatedMessage(chain));
                }
                changes.put(usage, target);
                return tree;
            }

            @Override
            SourceFile afterDeclarations(SourceFile sourceFile, ExecutionContext ctx) {
                SourceFile s = sourceFile;
                for (Map.Entry<Usage, GroupArtifact> change : changes.entrySet()) {
                    TreeVisitor<?, ExecutionContext> visitor = changeVisitor(change.getKey(), change.getValue());
                    if (visitor.isAcceptable(s, ctx)) {
                        s = (SourceFile) visitor.visitNonNull(s, ctx);
                    }
                }
                changes.clear();
                return s;
            }
        };
    }

    private static TreeVisitor<?, ExecutionContext> changeVisitor(Usage usage, GroupArtifact target) {
        String groupId = usage.getFrom().getGroupId();
        String artifactId = requireNonNull(usage.getFrom().getArtifactId());
        String newArtifactId = requireNonNull(target.getArtifactId());
        // Without a new version, neither recipe's scanner has anything to collect, so their visitors suffice
        return usage.getKind() == Kind.PLUGIN ?
                new ChangePluginGroupIdAndArtifactId(groupId, artifactId, target.getGroupId(), newArtifactId, null).getVisitor() :
                new ChangeDependency(groupId, artifactId, target.getGroupId(), newArtifactId, null, null, null, null).getVisitor();
    }

    private static @Nullable String context(List<Hop> chain) {
        for (Hop hop : chain) {
            if (hop.getRelocation().getContext() != null) {
                return hop.getRelocation().getContext();
            }
        }
        return null;
    }

    private static String relocatedMessage(List<Hop> chain) {
        String relocatedMessage = chain.stream()
                .map(hop -> hop.getTo().getGroupId() + ":" + hop.getTo().getArtifactId())
                .collect(joining(", then to ", "Relocated to ", ""));
        String context = context(chain);
        return context == null ? relocatedMessage : relocatedMessage + " as per \"" + context + "\"";
    }

    @Value
    static class Declaration {
        Kind kind;
        GroupArtifact from;

        @Nullable
        String version;

        boolean versionless;

        boolean managed;

        Repositories repositories;

        boolean isAvailableAt(GroupArtifact target) {
            if (versionless) {
                return repositories.manages(target);
            }
            return version != null && repositories.publishes(target, version);
        }
    }

    private abstract static class DeclarationVisitor extends TreeVisitor<Tree, ExecutionContext> {
        private final Accumulator acc;

        DeclarationVisitor(Accumulator acc) {
            this.acc = acc;
        }

        abstract <T extends Tree> T visitDeclaration(Declaration declaration, List<Hop> chain, T tree, ExecutionContext ctx);

        SourceFile afterDeclarations(SourceFile sourceFile, ExecutionContext ctx) {
            return sourceFile;
        }

        @Override
        public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
            return isGradleBuildFile(sourceFile) || isVersionCatalog(sourceFile) || sourceFile instanceof Xml.Document;
        }

        private static boolean isGradleBuildFile(SourceFile sourceFile) {
            String path = sourceFile.getSourcePath().toString();
            return sourceFile instanceof JavaSourceFile && (path.endsWith(".gradle") || path.endsWith(".gradle.kts"));
        }

        private static boolean isVersionCatalog(SourceFile sourceFile) {
            return sourceFile instanceof Toml.Document && sourceFile.getSourcePath().toString().endsWith(".versions.toml");
        }

        @Override
        public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (!(tree instanceof SourceFile)) {
                return tree;
            }
            SourceFile s = (SourceFile) tree;
            TreeVisitor<?, ExecutionContext> visitor = isGradleBuildFile(s) ? gradleVisitor() :
                    isVersionCatalog(s) ? versionCatalogVisitor() :
                    mavenVisitor();
            if (!visitor.isAcceptable(s, ctx)) {
                return s;
            }
            return afterDeclarations((SourceFile) visitor.visitNonNull(s, ctx), ctx);
        }

        private @Nullable Tree visitCoordinates(Kind kind, String groupId, String artifactId, @Nullable String version,
                                                boolean versionless, boolean managed, Repositories repositories,
                                                Tree tree, ExecutionContext ctx) {
            List<Hop> chain = relocations(groupId, artifactId, repositories);
            if (chain.isEmpty()) {
                return tree;
            }
            Declaration declaration = new Declaration(kind, new GroupArtifact(groupId, artifactId), version,
                    versionless, managed, repositories);
            return visitDeclaration(declaration, chain, tree, ctx);
        }

        private List<Hop> relocations(String groupId, String artifactId, Repositories repositories) {
            List<Hop> chain = new ArrayList<>();
            Set<GroupArtifact> visited = new HashSet<>();
            GroupArtifact current = new GroupArtifact(groupId, artifactId);
            visited.add(current);
            while (true) {
                String currentArtifactId = requireNonNull(current.getArtifactId());
                Relocation relocation = acc.getMigrations().get(current);
                if (relocation == null) {
                    relocation = acc.getMigrations().get(new GroupArtifact(current.getGroupId(), null));
                }
                if (relocation == null) {
                    return chain;
                }
                GroupArtifact to = new GroupArtifact(relocation.getTo().getGroupId(),
                        relocation.getTo().getArtifactId() == null ? currentArtifactId : relocation.getTo().getArtifactId());
                // A relocation of a whole group says nothing about whether each of its artifacts moved along
                if (relocation.getTo().getArtifactId() == null && !repositories.exists(to)) {
                    return chain;
                }
                if (!visited.add(to)) {
                    return chain;
                }
                chain.add(new Hop(to, relocation));
                current = to;
            }
        }

        private TreeVisitor<?, ExecutionContext> gradleVisitor() {
            return new JavaIsoVisitor<ExecutionContext>() {
                private final GradleDependency.Matcher dependencyMatcher = new GradleDependency.Matcher();

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                    GradleDependency dependency = dependencyMatcher.get(getCursor()).orElse(null);
                    if (dependency == null) {
                        return m;
                    }
                    String declaredVersion = dependency.getResolvedDependency().getRequested().getVersion();
                    String version = !StringUtils.isBlank(declaredVersion) ? declaredVersion :
                            dependency.getVersionVariable() != null && dependency.getResolvedDependency().getDepth() >= 0 ?
                                    dependency.getVersion() : null;
                    Repositories repositories = Repositories.of(getCursor().firstEnclosingOrThrow(SourceFile.class),
                            "classpath".equals(dependency.getConfigurationName()), ctx);
                    return (J.MethodInvocation) visitCoordinates(Kind.DEPENDENCY, dependency.getGroupId(),
                            dependency.getArtifactId(), version, false, false, repositories, m, ctx);
                }
            };
        }

        private TreeVisitor<?, ExecutionContext> versionCatalogVisitor() {
            return new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    if (!(tree instanceof Toml.Document)) {
                        return tree;
                    }
                    Cursor root = new Cursor(new Cursor(null, Cursor.ROOT_VALUE), tree);
                    VersionCatalog catalog = new VersionCatalog.Matcher().get(root).orElse(null);
                    if (catalog == null) {
                        return tree;
                    }
                    Map<String, String> versionDeclarations = catalog.getVersionDeclarations();
                    Repositories repositories = Repositories.of((SourceFile) tree, false, ctx);
                    return new VersionCatalogLibrary.Matcher().<ExecutionContext>asVisitor((library, c) -> visitCoordinates(
                            Kind.DEPENDENCY, library.getGroupId(), library.getArtifactId(),
                            library.getResolvedVersion(versionDeclarations), false, false, repositories,
                            library.getTree(), c)).visit(tree, ctx);
                }
            };
        }

        private TreeVisitor<?, ExecutionContext> mavenVisitor() {
            return new MavenIsoVisitor<ExecutionContext>() {
                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag t = super.visitTag(tag, ctx);
                    Kind kind = kind();
                    if (kind == null) {
                        return t;
                    }
                    ResolvedPom pom = getResolutionResult().getPom();
                    String groupId = pom.getValue(t.getChildValue("groupId")
                            .orElse(kind == Kind.PLUGIN ? "org.apache.maven.plugins" : null));
                    String artifactId = pom.getValue(t.getChildValue("artifactId").orElse(null));
                    if (groupId == null || artifactId == null) {
                        return t;
                    }
                    String declaredVersion = t.getChildValue("version").orElse(null);
                    String version = pom.getValue(declaredVersion);
                    return (Xml.Tag) visitCoordinates(kind, groupId, artifactId,
                            version == null || version.contains("${") ? null : version,
                            kind == Kind.DEPENDENCY && declaredVersion == null, isManagedDependencyTag(),
                            Repositories.of(getResolutionResult(), ctx), t, ctx);
                }

                private @Nullable Kind kind() {
                    if (DEPENDENCY_MATCHER.matches(getCursor()) || ANNOTATION_PROCESSOR_PATH_MATCHER.matches(getCursor())) {
                        return Kind.DEPENDENCY;
                    } else if (isPluginTag()) {
                        return Kind.PLUGIN;
                    } else if (PARENT_MATCHER.matches(getCursor())) {
                        return Kind.PARENT;
                    } else if (EXTENSION_MATCHER.matches(getCursor())) {
                        return Kind.EXTENSION;
                    }
                    return null;
                }
            };
        }
    }

    private static class Repositories {
        private final MavenPomDownloader downloader;

        @Nullable
        private final ResolvedPom pom;

        private final List<MavenRepository> repositories;

        private Repositories(MavenPomDownloader downloader, @Nullable ResolvedPom pom, List<MavenRepository> repositories) {
            this.downloader = downloader;
            this.pom = pom;
            this.repositories = repositories;
        }

        static Repositories of(MavenResolutionResult resolutionResult, ExecutionContext ctx) {
            ResolvedPom pom = resolutionResult.getPom();
            return new Repositories(new MavenPomDownloader(resolutionResult.getProjectPoms(), ctx), pom, pom.getRepositories());
        }

        static Repositories of(SourceFile sourceFile, boolean buildscript, ExecutionContext ctx) {
            List<MavenRepository> repositories = sourceFile.getMarkers().findFirst(GradleProject.class)
                    .map(gp -> buildscript ? gp.getBuildscript().getMavenRepositories() : gp.getMavenRepositories())
                    .orElse(emptyList());
            return new Repositories(new MavenPomDownloader(emptyMap(), ctx), null, repositories);
        }

        boolean exists(GroupArtifact ga) {
            return versions(ga) != null;
        }

        boolean publishes(GroupArtifact ga, String version) {
            List<String> versions = versions(ga);
            return versions != null && versions.contains(version);
        }

        boolean manages(GroupArtifact ga) {
            return pom != null && pom.getManagedVersion(ga.getGroupId(), requireNonNull(ga.getArtifactId()), null, null) != null;
        }

        private @Nullable List<String> versions(GroupArtifact ga) {
            try {
                return downloader.downloadMetadata(
                                new org.openrewrite.maven.tree.GroupArtifact(ga.getGroupId(), requireNonNull(ga.getArtifactId())),
                                pom, repositories)
                        .getVersioning()
                        .getVersions();
            } catch (MavenDownloadingException e) {
                return null;
            }
        }
    }
}
