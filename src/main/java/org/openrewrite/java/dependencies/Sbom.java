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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.Value;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

/**
 * A CycloneDX 1.6 Software Bill of Materials (SBOM).
 */
@Value
public class Sbom {

    @Nullable
    public static Sbom.Bom sbomFrom(Marker m) {
        if(m instanceof MavenResolutionResult) {
            return sbomFrom((MavenResolutionResult) m);
        } else if(m instanceof GradleProject) {
            return sbomFrom((GradleProject) m);
        }
        return null;
    }


    public static Sbom.Bom sbomFrom(MavenResolutionResult mrr) {
        return Bom.builder()
                .version(mrr.getPom().getVersion())
                .metadata(Metadata.builder()
                        .tools(singletonList(Tool.builder()
                                .vendor("OpenRewrite by Moderne")
                                .name("OpenRewrite CycloneDX")
                                .version("8.32.0")
                                .build()))
                        .component(componentFrom(mrr))
                        .build())
                .components(componentsFrom(mrr))
                .dependencies(dependenciesFrom(mrr))
                .build();
    }

    public static Sbom.Bom sbomFrom(GradleProject gp) {
        return Bom.builder()
                .version(gp.getVersion())
                .metadata(Metadata.builder()
                        .tools(singletonList(Tool.builder()
                                .vendor("OpenRewrite by Moderne")
                                .name("OpenRewrite CycloneDX")
                                .version("8.32.0")
                                .build()))
                        .component(componentFrom(gp))
                        .build())
                .components(componentsFrom(gp))
                .dependencies(dependenciesFrom(gp))
                .build();
    }

    private static Sbom.Component componentFrom(MavenResolutionResult mrr) {
        String groupId = mrr.getPom().getGroupId();
        String artifactId = mrr.getPom().getArtifactId();
        String version = mrr.getPom().getVersion();
        String bomRef = bomRefFrom(groupId, artifactId, version);
        return Component.builder()
                .bomRef(bomRef)
                .group(groupId)
                .name(artifactId)
                .version(version)
                .purl(bomRef)
                .build();
    }
    private static Sbom.Component componentFrom(GradleProject gp) {
        String groupId = gp.getGroup();
        String artifactId = gp.getName();
        String version = gp.getVersion();
        String bomRef = bomRefFrom(groupId, artifactId, version);
        return Component.builder()
                .bomRef(bomRef)
                .group(groupId)
                .name(artifactId)
                .version(version)
                .purl(bomRef)
                .build();
    }

    private static String bomRefFrom(@Nullable String groupId, String artifactId, @Nullable String version) {
        return String.format("pkg:maven/%s/%s@%s",
                groupId == null ? "" : groupId,
                artifactId,
                version == null ? "" : version);
    }

    private static List<Sbom.Component> componentsFrom(MavenResolutionResult mrr) {
        List<ResolvedDependency> compileDependencies = mrr.getDependencies().getOrDefault(Scope.Runtime, Collections.emptyList());
        List<ResolvedDependency> providedDependencies = mrr.getDependencies().getOrDefault(Scope.Provided, Collections.emptyList());
        return componentsFrom(compileDependencies, providedDependencies);
    }

    private static List<Sbom.Component> componentsFrom(GradleProject gp) {
        List<ResolvedDependency> compileDependencies = Optional.ofNullable(gp.getConfiguration("runtimeClasspath"))
                .map(GradleDependencyConfiguration::getDirectResolved)
                .orElseGet(Collections::emptyList);
        List<ResolvedDependency> providedDependencies = Optional.ofNullable(gp.getConfiguration("compileOnly"))
                .map(GradleDependencyConfiguration::getDirectResolved)
                .orElseGet(Collections::emptyList);
        return componentsFrom(compileDependencies, providedDependencies);
    }

    private static List<Component> componentsFrom(List<ResolvedDependency> compileDependencies, List<ResolvedDependency> providedDependencies) {
        List<Component> components = new ArrayList<>(compileDependencies.size() + providedDependencies.size());
        Set<String> seen = new HashSet<>();
        for (ResolvedDependency dep : compileDependencies) {
            String bomRef = bomRefFrom(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
            seen.add(bomRef);
            components.add(Component.builder()
                    .bomRef(bomRef)
                    .group(dep.getGroupId())
                    .name(dep.getArtifactId())
                    .version(dep.getVersion())
                    .scope("required")
                    .licenses(dep.getLicenses().stream()
                            .map(l -> License.builder()
                                    .name(l.getName())
                                    .build())
                            .collect(Collectors.toList()))
                    .purl(bomRef)
                    .build());
        }
        for (ResolvedDependency dep : providedDependencies) {
            String bomRef = bomRefFrom(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
            // Provided is a superset of Compile
            // Only add "optional" components for things not already recorded as "required"
            if (seen.add(bomRef)) {
                components.add(Component.builder()
                        .bomRef(bomRef)
                        .group(dep.getGroupId())
                        .name(dep.getArtifactId())
                        .version(dep.getVersion())
                        .scope("optional")
                        .purl(bomRef)
                        .build());
            }
        }

        return components;
    }

    private static List<Dependency> dependenciesFrom(MavenResolutionResult mrr) {
        List<ResolvedDependency> compileDependencies = mrr.getDependencies().getOrDefault(Scope.Runtime, Collections.emptyList());
        List<ResolvedDependency> providedDependencies = mrr.getDependencies().getOrDefault(Scope.Provided, Collections.emptyList());
        return dependenciesFrom(compileDependencies, providedDependencies);
    }

    private static List<Dependency> dependenciesFrom(GradleProject gp) {
        List<ResolvedDependency> compileDependencies = Optional.ofNullable(gp.getConfiguration("runtimeClasspath"))
                .map(GradleDependencyConfiguration::getDirectResolved)
                .orElseGet(Collections::emptyList);
        List<ResolvedDependency> providedDependencies = Optional.ofNullable(gp.getConfiguration("compileOnly"))
                .map(GradleDependencyConfiguration::getDirectResolved)
                .orElseGet(Collections::emptyList);
        return dependenciesFrom(compileDependencies, providedDependencies);
    }

    private static List<Dependency> dependenciesFrom(List<ResolvedDependency> compileDependencies, List<ResolvedDependency> providedDependencies) {
        List<Dependency> dependencies = new ArrayList<>(compileDependencies.size() + providedDependencies.size());

        Set<Dependency> seen = new HashSet<>();
        for (ResolvedDependency dep : compileDependencies) {
            Dependency dependency = dependencyFrom(dep);
            if (seen.add(dependency)) {
                dependencies.add(dependency);
            }
        }
        for (ResolvedDependency dep : providedDependencies) {
            Dependency dependency = dependencyFrom(dep);
            if (seen.add(dependency)) {
                dependencies.add(dependencyFrom(dep));
            }
        }
        return dependencies;
    }

    private static Dependency dependencyFrom(ResolvedDependency dep) {
        return Dependency.builder()
                .ref(bomRefFrom(dep.getGroupId(), dep.getArtifactId(), dep.getVersion()))
                .dependencies(dep.getDependencies().stream()
                        .map(Sbom::dependencyFrom)
                        .collect(Collectors.toList()))
                .build();
    }

    @Builder
    @Value
    @JacksonXmlRootElement(localName = "bom")
    @JsonPropertyOrder({"xmlns", "version"})
    public static class Bom {
        @JacksonXmlProperty(isAttribute = true)
        String xmlns = "http://cyclonedx.org/schema/bom/1.6";

        @JacksonXmlProperty(isAttribute = true)
        String version;

        Metadata metadata;
        @JacksonXmlElementWrapper(localName = "components")
        @JacksonXmlProperty(localName = "component")
        List<Component> components;

        @JacksonXmlElementWrapper(localName = "dependencies")
        @JacksonXmlProperty(localName = "dependency")
        List<Dependency> dependencies;
    }

    @Builder
    @Value
    public static class Metadata {
        @JacksonXmlElementWrapper(localName = "tools")
        @JacksonXmlProperty(localName = "tool")
        List<Tool> tools;
        Component component;
    }

    @Builder
    @Value
    public static class Tool {
        String vendor;
        String name;
        String version;
    }

    @Builder
    @Value
    @JsonPropertyOrder({"xmlns", "type", "group", "name", "version", "version"})
    public static class Component {
        @JacksonXmlProperty(isAttribute = true, localName = "bom-ref")
        String bomRef;

        @JacksonXmlProperty(isAttribute = true)
        @Nullable
        String type;

        String group;
        String name;
        String version;
        @Nullable
        String scope;
        @JacksonXmlElementWrapper(localName = "licenses")
        @JacksonXmlProperty(localName = "license")
        List<License> licenses;
        String purl;
    }

    @Builder
    @Value
    public static class License {
        String id;
        String name;
    }

    @Builder
    @Value
    public static class Dependency {
        @JacksonXmlProperty(isAttribute = true)
        String ref;
        @JacksonXmlElementWrapper(useWrapping = false)
        List<Dependency> dependencies;
    }
}
