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

import lombok.*;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;

import java.util.Arrays;
import java.util.List;



@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UpgradeDependencyVersion extends Recipe {
    @Option(displayName = "Group",
        description = "The first part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
        example = "com.fasterxml.jackson*")
    private final String groupId;

    @Option(displayName = "Artifact",
        description = "The second part of a dependency coordinate `com.google.guava:guava:VERSION`. This can be a glob expression.",
        example = "jackson-module*")
    private final String artifactId;

    @Option(displayName = "New version",
        description = "An exact version number or node-style semver selector used to select the version number. ",
        example = "29.X")
    private final String newVersion;

    @Option(displayName = "Version pattern",
        description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example," +
                      "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
        example = "-jre",
        required = false)
    @Nullable
    private final String versionPattern;

    @Option(displayName = "Override managed version",
        description = "For Maven project only, This flag can be set to explicitly override a managed dependency's version. The default for this flag is `false`.",
        required = false)
    @Nullable
    private final Boolean overrideManagedVersion;

    @Option(displayName = "Retain versions",
        description = "For Maven project only, Accepts a list of GAVs. For each GAV, if it is a project direct dependency, and it is removed "
                      + "from dependency management after the changes from this recipe, then it will be retained with an explicit version. "
                      + "The version can be omitted from the GAV to use the old value from dependency management",
        example = "com.jcraft:jsch",
        required = false)
    @Nullable
    private final List<String> retainVersions;

    @Override
    public String getDisplayName() {
        return "Upgrade Gradle or Maven dependency versions";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "For Gradle projects, upgrade the version of a dependency in a build.gradle file. " +
               "Supports updating dependency declarations of various forms:\n" +
               "* `String` notation: `\"group:artifact:version\"` \n" +
               "* `Map` notation: `group: 'group', name: 'artifact', version: 'version'`\n" +
               "Can update version numbers which are defined earlier in the same file in variable declarations.\n\n" +
               "For Maven projects, upgrade the version of a dependency by specifying a group and (optionally) an " +
               "artifact using Node Semver advanced range selectors, allowing more precise control over version " +
               "updates to patch or minor releases.";
    }

    @Nullable
    private org.openrewrite.gradle.UpgradeDependencyVersion upgradeGradleDependencyVersion;

    @Nullable
    private org.openrewrite.maven.UpgradeDependencyVersion upgradeMavenDependencyVersion;

    @Override
    public List<Recipe> getRecipeList() {
        if (upgradeGradleDependencyVersion == null && upgradeMavenDependencyVersion == null) {
            upgradeGradleDependencyVersion = new org.openrewrite.gradle.UpgradeDependencyVersion(groupId, artifactId, newVersion, versionPattern);
            upgradeMavenDependencyVersion = new org.openrewrite.maven.UpgradeDependencyVersion(groupId, artifactId, newVersion, versionPattern, overrideManagedVersion, retainVersions);
        }
        return Arrays.asList(
            upgradeGradleDependencyVersion,
            upgradeMavenDependencyVersion
        );
    }
}
