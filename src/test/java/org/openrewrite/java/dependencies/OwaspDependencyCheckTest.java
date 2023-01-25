/*
 * Copyright 2021 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.data.nexus.MavenArtifact;
import org.owasp.dependencycheck.data.update.exception.UpdateException;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Vulnerability;
import org.owasp.dependencycheck.exception.ExceptionCollection;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

public class OwaspDependencyCheckTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OwaspDependencyCheck());
    }

    @Test
    void exerciseEngine() throws UpdateException, ExceptionCollection {
        OwaspDependencyCheck check = new OwaspDependencyCheck();
        try (Engine engine = check.getEngine()) {
            Dependency dependency = new Dependency();
            MavenArtifact mavenArtifact = new MavenArtifact("org.apache.logging.log4j", "log4j", "2.12.2");
            dependency.addAsEvidence("resolved", mavenArtifact, Confidence.HIGHEST);
            engine.addDependency(dependency);
            engine.analyzeDependencies();
            Set<Vulnerability> vulnerabilities = engine.getDependencies()[0].getVulnerabilities();
            assertThat(vulnerabilities).isNotEmpty();
        }
    }

    @Disabled
    @Test
    void gradle() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                id 'java'
              }
                            
              repositories {
                  mavenCentral()
              }
                            
              dependencies {
                  implementation 'org.apache.logging.log4j:log4j:2.12.2'
              }
              """
          )
        );
    }

    @Test
    void maven() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                
                <dependencies>
                  <dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j</artifactId>
                    <version>2.12.2</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
              
                <dependencies>
                  <!--~~(This dependency includes org.apache.logging.log4j:log4j:2.12.2 which has the following vulnerabilities:
              CVE-2021-44832 (6.6) - Apache Log4j2 versions 2.0-beta7 through 2.17.0 (excluding security fix releases 2.3.2 and 2.12.4) are vulnerable to a remote code execution (RCE) attack when a configuration uses a JDBC Appender with a JNDI LDAP data source URI when an attacker has control of the target LDAP server. This issue is fixed by limiting JNDI data source names to the java protocol in Log4j2 versions 2.17.1, 2.12.4, and 2.3.2.
              CVE-2021-45105 (5.9) - Apache Log4j2 versions 2.0-alpha1 through 2.16.0 (excluding 2.12.3 and 2.3.1) did not protect from uncontrolled recursion from self-referential lookups. This allows an attacker with control over Thread Context Map data to cause a denial of service when a crafted string is interpreted. This issue was fixed in Log4j 2.17.0, 2.12.3, and 2.3.1.
              CVE-2020-9488 (3.7) - Improper validation of certificate with host mismatch in Apache Log4j SMTP appender. This could allow an SMTPS connection to be intercepted by a man-in-the-middle attack which could leak any log messages sent through that appender. Fixed in Apache Log4j 2.12.3 and 2.13.1)~~>--><dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j</artifactId>
                    <version>2.12.2</version>
                  </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }
}
