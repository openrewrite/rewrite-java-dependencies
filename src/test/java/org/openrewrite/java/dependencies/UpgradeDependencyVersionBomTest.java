/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;

/**
 * Test case to reproduce an issue with `UpgradeDependencyVersion` recipe when upgrading a Gradle dependency in a BOM import.
 * 
 * Key findings from investigation:
 * - Works with 'implementation' configuration (direct dependency)
 * - Fails with BOM imports via dependency management plugin
 * - Regular dependencies support interpolated strings
 * - BOM imports do NOT support interpolated strings
 */ 
class UpgradeDependencyVersionBomTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new org.openrewrite.java.dependencies.UpgradeDependencyVersion(
            "com.google.cloud",
            "spring-cloud-gcp-dependencies",
            "7.2.x",
            null,
            null,
            null
        ))
        .beforeRecipe(withToolingApi())
        .parser(GradleParser.builder());
    }

    /**
     * This test demonstrates that direct 'implementation' dependencies work correctly.
     * This is the workaround mentioned in the Jira ticket.
     */
    @Test
    void upgradesDirectImplementationDependency() {
        rewriteRun(
            mavenProject("test-project",
                buildGradle(
                    """
                    plugins {
                        id 'java'
                        id 'org.springframework.boot' version '3.1.0'
                    }
                    
                    repositories {
                        mavenCentral()
                    }
                    
                    dependencies {
                        implementation 'org.springframework.boot:spring-boot-starter-web'
                        implementation 'com.google.cloud:spring-cloud-gcp-starter'
                        implementation 'com.google.cloud:spring-cloud-gcp-dependencies:7.1.0'
                        testImplementation 'org.springframework.boot:spring-boot-starter-test'
                        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                    }
                    """,
                    """
                    plugins {
                        id 'java'
                        id 'org.springframework.boot' version '3.1.0'
                    }
                    
                    repositories {
                        mavenCentral()
                    }
                    
                    dependencies {
                        implementation 'org.springframework.boot:spring-boot-starter-web'
                        implementation 'com.google.cloud:spring-cloud-gcp-starter'
                        implementation 'com.google.cloud:spring-cloud-gcp-dependencies:7.2.0'
                        testImplementation 'org.springframework.boot:spring-boot-starter-test'
                        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                    }
                    """
                )
            )
        );
    }

    /**
     * This test demonstrates the FAILING case: BOM import with literal version.
     * Expected: Version should be upgraded from 7.1.0 to 7.2.x
     * Actual: No change occurs (this is the bug)
     */
    @Test
    void upgradesBomImportWithLiteralVersion() {
        rewriteRun(
            mavenProject("test-project",
                buildGradle(
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.1.0'
                    id 'io.spring.dependency-management' version '1.1.0'
                }
                
                repositories {
                    mavenCentral()
                }
                
                dependencyManagement {
                    imports {
                        mavenBom 'com.google.cloud:spring-cloud-gcp-dependencies:7.1.0'
                    }
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'com.google.cloud:spring-cloud-gcp-starter'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """,
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.1.0'
                    id 'io.spring.dependency-management' version '1.1.0'
                }
                
                repositories {
                    mavenCentral()
                }
                
                dependencyManagement {
                    imports {
                        mavenBom 'com.google.cloud:spring-cloud-gcp-dependencies:7.2.0'
                    }
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'com.google.cloud:spring-cloud-gcp-starter'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """
                )
            )
        );
    }

    /**
     * This test demonstrates another FAILING case: BOM import with interpolated string (variable).
     * This is likely closer to what the customer has in their actual build.gradle.
     * 
     * Expected: Variable value should be upgraded from 7.1.0 to 7.2.x
     * Actual: No change occurs (this is the bug)
     */
    @Test
    void upgradesBomImportWithInterpolatedString() {
        rewriteRun(
            mavenProject("test-project",
                buildGradle(
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.1.0'
                    id 'io.spring.dependency-management' version '1.1.0'
                }
                
                repositories {
                    mavenCentral()
                }
                
                ext {
                    springCloudGcpVersion = '7.1.0'
                }
                
                dependencyManagement {
                    imports {
                        mavenBom "com.google.cloud:spring-cloud-gcp-dependencies:${springCloudGcpVersion}"
                    }
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'com.google.cloud:spring-cloud-gcp-starter'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """,
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.1.0'
                    id 'io.spring.dependency-management' version '1.1.0'
                }
                
                repositories {
                    mavenCentral()
                }
                
                ext {
                    springCloudGcpVersion = '7.2.0'
                }
                
                dependencyManagement {
                    imports {
                        mavenBom "com.google.cloud:spring-cloud-gcp-dependencies:${springCloudGcpVersion}"
                    }
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'com.google.cloud:spring-cloud-gcp-starter'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """
                )
            )
        );
    }

    /**
     * This test demonstrates yet another potential FAILING case: using set() syntax for properties.
     */
    @Test
    void upgradesBomImportWithSetSyntax() {
        rewriteRun(
            mavenProject("test-project",
                buildGradle(
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.1.0'
                    id 'io.spring.dependency-management' version '1.1.0'
                }
                
                repositories {
                    mavenCentral()
                }
                
                ext.set("springCloudGcpVersion", "7.1.0")
                
                dependencyManagement {
                    imports {
                        mavenBom "com.google.cloud:spring-cloud-gcp-dependencies:${springCloudGcpVersion}"
                    }
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'com.google.cloud:spring-cloud-gcp-starter'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """,
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.1.0'
                    id 'io.spring.dependency-management' version '1.1.0'
                }
                
                repositories {
                    mavenCentral()
                }
                
                ext.set("springCloudGcpVersion", "7.2.0")
                
                dependencyManagement {
                    imports {
                        mavenBom "com.google.cloud:spring-cloud-gcp-dependencies:${springCloudGcpVersion}"
                    }
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'com.google.cloud:spring-cloud-gcp-starter'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """
                )
            )
        );
    }
}

