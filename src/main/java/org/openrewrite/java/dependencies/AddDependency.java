/*
 * Copyright 2023 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;

import java.util.Arrays;
import java.util.List;


@Value
@EqualsAndHashCode(callSuper = true)
public class AddDependency extends Recipe {
    // Gradle and Maven shared parameters
    @Option(displayName = "Group",
        description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`.",
        example = "com.google.guava")
    String groupId;

    @Option(displayName = "Artifact",
        description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`",
        example = "guava")
    String artifactId;

    @Option(displayName = "Version",
        description = "An exact version number or node-style semver selector used to select the version number.",
        example = "29.X",
        required = false)
    @Nullable
    String version;

    @Option(displayName = "Version pattern",
        description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example, " +
                      "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
        example = "-jre",
        required = false)
    @Nullable
    String versionPattern;

    @Option(displayName = "Only if using",
        description = "Used to determine if the dependency will be added and in which scope it should be placed.",
        example = "org.junit.jupiter.api.*")
    String onlyIfUsing;

    @Option(displayName = "Classifier",
        description = "A classifier to add. Commonly used to select variants of a library.",
        example = "test",
        required = false)
    @Nullable
    String classifier;

    @Option(displayName = "Family pattern",
        description = "A pattern, applied to groupIds, used to determine which other dependencies should have aligned version numbers. " +
                      "Accepts '*' as a wildcard character.",
        example = "com.fasterxml.jackson*",
        required = false)
    @Nullable
    String familyPattern;

    // Gradle only parameters
    @Option(displayName = "Extension",
        description = "For Gradle only, The extension of the dependency to add. If omitted Gradle defaults to assuming the type is \"jar\".",
        example = "jar",
        required = false)
    @Nullable
    String extension;


    @Option(displayName = "Configuration",
        description = "For Gradle only, A configuration to use when it is not what can be inferred from usage. Most of the time this will be left empty, but " +
                      "is used when adding a new as of yet unused dependency.",
        example = "implementation",
        required = false)
    @Nullable
    String configuration;

    // Maven only parameters
    @Option(displayName = "Scope",
        description = "For Maven only, A scope to use when it is not what can be inferred from usage. Most of the time this will be left empty, but " +
                      "is used when adding a runtime, provided, or import dependency.",
        example = "runtime",
        valid = {"import", "runtime", "provided"},
        required = false)
    @Nullable
    String scope;

    @Option(displayName = "Releases only",
        description = "For Maven only, Whether to exclude snapshots from consideration when using a semver selector",
        required = false)
    @Nullable
    Boolean releasesOnly;

    @Option(displayName = "Type",
        description = "For Maven only, The type of dependency to add. If omitted Maven defaults to assuming the type is \"jar\".",
        valid = {"jar", "pom", "war"},
        example = "jar",
        required = false)
    @Nullable
    String type;

    @Option(displayName = "Optional",
        description = "Set the value of the `<optional>` tag. No `<optional>` tag will be added when this is `null`.",
        required = false)
    @Nullable
    Boolean optional;

    @Option(displayName = "Accept transitive",
        description = "For Maven only, Default false. If enabled, the dependency will not be added if it is already on the classpath as a transitive dependency.",
        example = "true",
        required = false)
    @Nullable
    Boolean acceptTransitive;

    @Override
    public String getDisplayName() {
        return "Add Gradle or Maven dependency";
    }

    @Override
    public String getDescription() {
        return "For a Gradle project, add a gradle dependency to a `build.gradle` file in the correct configuration " +
               "based on where it is used. Or For a maven project, Add a Maven dependency to a `pom.xml` file in the " +
               "correct scope based on where it is used.";
    }

    @Nullable
    org.openrewrite.gradle.AddDependency addGradleDependency;

    @Nullable
    org.openrewrite.maven.AddDependency addMavenDependency;

    public AddDependency(
        String groupId,
        String artifactId,
        @Nullable String version,
        @Nullable String versionPattern,
        String onlyIfUsing,
        @Nullable String classifier,
        @Nullable String familyPattern,
        @Nullable String extension,
        @Nullable String configuration,
        @Nullable String scope,
        @Nullable Boolean releasesOnly,
        @Nullable String type,
        @Nullable Boolean optional,
        @Nullable Boolean acceptTransitive) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionPattern = versionPattern;
        this.onlyIfUsing = onlyIfUsing;
        this.classifier = classifier;
        this.familyPattern = familyPattern;
        this.extension = extension;
        this.configuration = configuration;
        this.scope = scope;
        this.releasesOnly = releasesOnly;
        this.type = type;
        this.optional = optional;
        this.acceptTransitive = acceptTransitive;
        addGradleDependency = new org.openrewrite.gradle.AddDependency(groupId, artifactId, version, versionPattern,
            configuration, onlyIfUsing, classifier, extension, familyPattern, acceptTransitive);
        String versionForMaven = version != null ? version : "latest.release";
        addMavenDependency = new org.openrewrite.maven.AddDependency(groupId, artifactId, versionForMaven,
            versionPattern, scope, releasesOnly, onlyIfUsing, type, classifier, optional, familyPattern,
            acceptTransitive);
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
            addGradleDependency,
            addMavenDependency
        );
    }
}
