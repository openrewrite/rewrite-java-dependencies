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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.xml.SemanticallyEqual;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class SoftwareBillOfMaterials extends ScanningRecipe<SoftwareBillOfMaterials.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Software bill of materials";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Produces a software bill of materials (SBOM) for a project. An SBOM is a complete list of all dependencies " +
               "used in a project, including transitive dependencies. The produced SBOM is in the [CycloneDX](https://cyclonedx.org/) XML format. " +
               "Supports Gradle and Maven. " +
               "Places a file named sbom.xml adjacent to the Gradle or Maven build file.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("CycloneDX");
    }

    public static class Accumulator {
        Set<Path> existingSboms = new LinkedHashSet<>();
        Set<Path> sbomPaths = new LinkedHashSet<>();
        Map<Path, Marker> sbomPathToDependencyMarker = new HashMap<>();
    }

    private static final XmlMapper xmlMapper = (XmlMapper) new XmlMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        //noinspection NullableProblems
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                SourceFile s = (SourceFile) tree;
                if (s.getSourcePath().toString().endsWith("sbom.xml")) {
                    acc.existingSboms.add(s.getSourcePath());
                    return tree;
                }
                s.getMarkers().getMarkers()
                        .stream()
                        .filter(marker -> marker instanceof GradleProject || marker instanceof MavenResolutionResult)
                        .forEach(e -> {
                            String sbomPathString = PathUtils.separatorsToUnix(s.getSourcePath().toString());
                            sbomPathString = sbomPathString.substring(0, sbomPathString.lastIndexOf("/") + 1) + "sbom.xml";
                            Path sbomPath = Paths.get(sbomPathString);
                            acc.sbomPaths.add(sbomPath);
                            acc.sbomPathToDependencyMarker.put(sbomPath, e);
                        });
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, Collection<SourceFile> generatedInThisCycle, ExecutionContext ctx) {
        Set<Path> newSbomPaths = new LinkedHashSet<>(acc.sbomPaths);
        newSbomPaths.removeAll(acc.existingSboms);
        List<Xml.Document> newSboms = new ArrayList<>();
        XmlParser xmlParser = XmlParser.builder().build();
        for (Path sbomPath : newSbomPaths) {
            xmlParser.parse(ctx, "<bom></bom>")
                    .map(it -> (Xml.Document) it.withSourcePath(sbomPath))
                    .findAny()
                    .ifPresent(newSboms::add);
        }
        return newSboms;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new XmlVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (!acc.sbomPaths.contains(document.getSourcePath())) {
                    return document;
                }
                Marker marker = acc.sbomPathToDependencyMarker.get(document.getSourcePath());
                if (marker != null) {
                    Sbom.Bom sbom = Sbom.sbomFrom(marker);
                    try {
                        @Language("xml")
                        String rawSbom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                         xmlMapper.writeValueAsString(sbom)
                                                 .replaceAll("\r", "") + "\n";
                        XmlParser xmlParser = XmlParser.builder().build();
                        //noinspection OptionalGetWithoutIsPresent
                        Xml.Document d = xmlParser.parse(rawSbom)
                                .map(it -> it.withSourcePath(document.getSourcePath())
                                        .withId(document.getId()))
                                .map(Xml.Document.class::cast)
                                .findAny()
                                .get();
                        if (SemanticallyEqual.areEqual(document, d)) {
                            return document;
                        }
                        return d;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
                return document;
            }
        };
    }
}
