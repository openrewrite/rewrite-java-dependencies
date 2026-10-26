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
import org.openrewrite.gradle.IsBuildGradle;
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
import org.openrewrite.maven.tree.MavenMetadata;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.Plugin;
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
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = false)
@Value
public class RelocatedDependencyCheck extends ScanningRecipe<RelocatedDependencyCheck.Accumulator> {
    private static final XPathMatcher DEPENDENCY_MATCHER = new XPathMatcher("//dependencies/dependency");
    private static final XPathMatcher ANNOTATION_PROCESSOR_PATH_MATCHER = new XPathMatcher("//annotationProcessorPaths/path");
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
            Set<@Nullable GroupArtifact> candidates = new HashSet<>(targets.getOrDefault(usage, emptySet()));
            if (!managedInRepository.contains(usage.getFrom())) {
                candidates.addAll(versionlessTargets.getOrDefault(usage, emptySet()));
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
                Usage usage = declaration.getUsage();
                if (usage.getKind() != Kind.DEPENDENCY && usage.getKind() != Kind.PLUGIN) {
                    return tree;
                }
                if (declaration.isManaged()) {
                    acc.getManagedInRepository().add(usage.getFrom());
                }
                Hop hop = furthestAvailable(declaration, chain);
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
                Usage usage = declaration.getUsage();
                Hop last = chain.get(chain.size() - 1);
                String context = context(chain);
                report.insertRow(ctx, new RelocatedDependencyReport.Row(
                        usage.getFrom().getGroupId(), usage.getFrom().getArtifactId(),
                        last.getTo().getGroupId(), last.getTo().getArtifactId(),
                        context));

                GroupArtifact target = Boolean.TRUE.equals(changeDependencies) ? acc.changeTo(usage) : null;
                if (target == null) {
                    return SearchResult.found(tree, relocatedMessage(chain, context));
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

    private static String relocatedMessage(List<Hop> chain, @Nullable String context) {
        String message = chain.stream()
                .map(hop -> hop.getTo().getGroupId() + ":" + hop.getTo().getArtifactId())
                .collect(joining(", then to ", "Relocated to ", ""));
        return context == null ? message : message + " as per \"" + context + "\"";
    }

    @Value
    static class Declaration {
        Usage usage;

        @Nullable
        String version;

        boolean versionless;

        boolean managed;

        Repositories repositories;

        static Declaration dependency(String groupId, String artifactId, @Nullable String version, Repositories repositories) {
            return new Declaration(new Usage(Kind.DEPENDENCY, new GroupArtifact(groupId, artifactId)), version,
                    false, false, repositories);
        }

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
            return sourceFile instanceof JavaSourceFile && IsBuildGradle.matches(sourceFile.getSourcePath());
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

        private <T extends Tree> T visitIfRelocated(Declaration declaration, T tree, ExecutionContext ctx) {
            List<Hop> chain = relocations(declaration.getUsage().getFrom(), declaration.getRepositories());
            return chain.isEmpty() ? tree : visitDeclaration(declaration, chain, tree, ctx);
        }

        private List<Hop> relocations(GroupArtifact from, Repositories repositories) {
            List<Hop> chain = new ArrayList<>();
            Set<GroupArtifact> visited = new HashSet<>();
            GroupArtifact current = from;
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

                @Nullable
                private Repositories repositories;

                @Nullable
                private Repositories buildscriptRepositories;

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                    GradleDependency dependency = dependencyMatcher.get(getCursor()).orElse(null);
                    if (dependency == null) {
                        return m;
                    }
                    return visitIfRelocated(Declaration.dependency(dependency.getGroupId(), dependency.getArtifactId(),
                            version(dependency), repositories("classpath".equals(dependency.getConfigurationName()), ctx)), m, ctx);
                }

                private @Nullable String version(GradleDependency dependency) {
                    String declaredVersion = dependency.getResolvedDependency().getRequested().getVersion();
                    if (!StringUtils.isBlank(declaredVersion)) {
                        return declaredVersion;
                    }
                    if (dependency.getVersionVariable() != null && dependency.getResolvedDependency().getDepth() >= 0) {
                        return dependency.getVersion();
                    }
                    return null;
                }

                private Repositories repositories(boolean buildscript, ExecutionContext ctx) {
                    if (buildscript) {
                        if (buildscriptRepositories == null) {
                            buildscriptRepositories = Repositories.of(getCursor().firstEnclosingOrThrow(SourceFile.class), true, ctx);
                        }
                        return buildscriptRepositories;
                    }
                    if (repositories == null) {
                        repositories = Repositories.of(getCursor().firstEnclosingOrThrow(SourceFile.class), false, ctx);
                    }
                    return repositories;
                }
            };
        }

        private TreeVisitor<?, ExecutionContext> versionCatalogVisitor() {
            return new VersionCatalog.Matcher().asVisitor((catalog, ctx) -> {
                Map<String, String> versionDeclarations = catalog.getVersionDeclarations();
                Repositories repositories = Repositories.of(catalog.getCursor().firstEnclosingOrThrow(SourceFile.class), false, ctx);
                return new VersionCatalogLibrary.Matcher().<ExecutionContext>asVisitor((library, c) -> visitIfRelocated(
                        Declaration.dependency(library.getGroupId(), library.getArtifactId(),
                                library.getResolvedVersion(versionDeclarations), repositories),
                        library.getTree(), c)).visit(catalog.getTree(), ctx, catalog.getCursor().getParentOrThrow());
            });
        }

        private TreeVisitor<?, ExecutionContext> mavenVisitor() {
            return new MavenIsoVisitor<ExecutionContext>() {
                @Nullable
                private Repositories repositories;

                @Nullable
                private Repositories pluginRepositories;

                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag t = super.visitTag(tag, ctx);
                    Kind kind = kind(t);
                    if (kind == null) {
                        return t;
                    }
                    ResolvedPom pom = getResolutionResult().getPom();
                    String groupId = pom.getValue(t.getChildValue("groupId")
                            .orElse(kind == Kind.PLUGIN ? Plugin.PLUGIN_DEFAULT_GROUPID : null));
                    String artifactId = pom.getValue(t.getChildValue("artifactId").orElse(null));
                    if (groupId == null || artifactId == null) {
                        return t;
                    }
                    String declaredVersion = t.getChildValue("version").orElse(null);
                    String version = pom.getValue(declaredVersion);
                    return visitIfRelocated(new Declaration(
                            new Usage(kind, new GroupArtifact(groupId, artifactId)),
                            version == null || version.contains("${") ? null : version,
                            kind == Kind.DEPENDENCY && declaredVersion == null,
                            isManagedDependencyTag(),
                            kind == Kind.PLUGIN ? pluginRepositories(ctx) : repositories(ctx)), t, ctx);
                }

                private @Nullable Kind kind(Xml.Tag tag) {
                    switch (tag.getName()) {
                        case "dependency":
                            return DEPENDENCY_MATCHER.matches(getCursor()) ? Kind.DEPENDENCY : null;
                        case "path":
                            return ANNOTATION_PROCESSOR_PATH_MATCHER.matches(getCursor()) ? Kind.DEPENDENCY : null;
                        case "plugin":
                            return isPluginTag() ? Kind.PLUGIN : null;
                        case "parent":
                            return isParentTag() ? Kind.PARENT : null;
                        case "extension":
                            return EXTENSION_MATCHER.matches(getCursor()) ? Kind.EXTENSION : null;
                        default:
                            return null;
                    }
                }

                private Repositories repositories(ExecutionContext ctx) {
                    if (repositories == null) {
                        repositories = new Repositories((g, a) -> downloadMetadata(g, a, ctx), getResolutionResult().getPom());
                    }
                    return repositories;
                }

                private Repositories pluginRepositories(ExecutionContext ctx) {
                    if (pluginRepositories == null) {
                        pluginRepositories = new Repositories((g, a) -> downloadPluginMetadata(g, a, ctx), getResolutionResult().getPom());
                    }
                    return pluginRepositories;
                }
            };
        }
    }

    @FunctionalInterface
    private interface MetadataDownloader {
        MavenMetadata download(String groupId, String artifactId) throws MavenDownloadingException;
    }

    private static class Repositories {
        private final MetadataDownloader downloader;

        @Nullable
        private final ResolvedPom pom;

        private final Map<GroupArtifact, @Nullable List<String>> versions = new HashMap<>();

        Repositories(MetadataDownloader downloader, @Nullable ResolvedPom pom) {
            this.downloader = downloader;
            this.pom = pom;
        }

        static Repositories of(SourceFile sourceFile, boolean buildscript, ExecutionContext ctx) {
            List<MavenRepository> repositories = sourceFile.getMarkers().findFirst(GradleProject.class)
                    .map(gp -> buildscript ? gp.getBuildscript().getMavenRepositories() : gp.getMavenRepositories())
                    .orElse(emptyList());
            MavenPomDownloader downloader = new MavenPomDownloader(emptyMap(), ctx);
            return new Repositories((g, a) -> downloader.downloadMetadata(
                    new org.openrewrite.maven.tree.GroupArtifact(g, a), null, repositories), null);
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
            if (!versions.containsKey(ga)) {
                List<String> found;
                try {
                    found = downloader.download(ga.getGroupId(), requireNonNull(ga.getArtifactId())).getVersioning().getVersions();
                } catch (MavenDownloadingException e) {
                    found = null;
                }
                versions.put(ga, found);
            }
            return versions.get(ga);
        }
    }
}
