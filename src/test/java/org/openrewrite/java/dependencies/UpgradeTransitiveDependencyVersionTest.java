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


import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeTransitiveDependencyVersionTest implements RewriteTest {
    String myOrganizationsInternalMavenRepoURL = "https://devops.www.MYCOMPANY.com/artifactory/public-release-virtual";
    
    @DocumentExample("Upgrade maven transitive dependency")
    @Test
    void upgradeSnappyInMavenProject() {
      rewriteRun(
//Fails. Makes no changes. Per debugger, it goes into a gradle visitor instead of the Maven visitor:
          spec -> spec.recipe(new org.openrewrite.java.dependencies.UpgradeTransitiveDependencyVersion("org.xerial.snappy", "snappy-java", "1.1.10.5", null, null, null, null, null, null, null))
//Works:
//        spec -> spec.recipe(new org.openrewrite.maven.UpgradeTransitiveDependencyVersion("org.xerial.snappy", "snappy-java", "1.1.10.5", null, null, null, null, null, null, null))
//Below makes no changes. Maybe by design. I just want the version to be forced upward regardless of whether it is a direct or transitive...
//          spec -> spec.recipe(new org.openrewrite.java.dependencies.UpgradeDependencyVersion("org.xerial.snappy", "snappy-java", "1.1.10.5", null, true, null))
/* Enable when you're firewalled and need to use a local repo
          .executionContext(
            MavenExecutionContextView
              .view(new InMemoryExecutionContext())
              .setRepositories(List.of(
                MavenRepository.builder().id("jenkins").uri(myOrganizationsInternalMavenRepoURL).build()
              ))

          )*/,
        pomXml(
          """
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

                <modelVersion>4.0.0</modelVersion>
               
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
               
                <dependencies>
                    <!-- Pulls in snappy-java transitively -->
                    <dependency>
                        <groupId>org.apache.spark</groupId>
                        <artifactId>spark-core_2.12</artifactId>
                        <version>2.4.4</version>
                    </dependency>
                </dependencies>
            </project>

            """,
          """
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

                <modelVersion>4.0.0</modelVersion>
          
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.xerial.snappy</groupId>
                            <artifactId>snappy-java</artifactId>
                            <version>1.1.10.5</version>
                        </dependency>                          
                    </dependencies>
                </dependencyManagement>
          
                <dependencies>
                    <!-- Pulls in snappy-java transitively -->
                    <dependency>
                        <groupId>org.apache.spark</groupId>
                        <artifactId>spark-core_2.12</artifactId>
                        <version>2.4.4</version>
                    </dependency>
                </dependencies>
            </project>

            """
        )
      );
  }
    
}
