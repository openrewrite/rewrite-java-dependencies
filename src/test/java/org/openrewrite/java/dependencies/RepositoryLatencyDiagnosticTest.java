/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.HttpSenderExecutionContextView;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.java.dependencies.table.RepositoryResponseLatency;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

class RepositoryLatencyDiagnosticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        // Keep the request count low so tests, which hit the real network, stay quick.
        spec.recipe(new RepositoryLatencyDiagnostic(null, null, 2));
    }

    // CI may inject ~/.m2/settings.xml with `<mirrorOf>*</mirrorOf>` to route Maven through a cache, which would
    // rewrite the pom-declared repositories. Force empty settings so the recipe sees the declared repositories.
    private static MavenExecutionContextView emptySettingsContext() {
        MavenExecutionContextView ctx = MavenExecutionContextView.view(new InMemoryExecutionContext());
        MavenSettings emptySettings = MavenSettings.parse(new Parser.Input(Path.of("settings.xml"), () -> new ByteArrayInputStream(
          //language=xml
          """
            <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd"/>
            """.getBytes())), ctx);
        ctx.setMavenSettings(emptySettings);
        return ctx;
    }

    @DocumentExample
    @Test
    void mavenCentralResponds() {
        rewriteRun(
          spec -> spec
            .dataTable(RepositoryResponseLatency.Row.class, rows ->
              assertThat(rows).anySatisfy(row -> {
                  assertThat(row.getRepositoryUri()).isEqualTo("https://repo.maven.apache.org/maven2");
                  assertThat(row.getMetadataUri())
                    .isEqualTo("https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-core/maven-metadata.xml");
                  assertThat(row.getGroupArtifact()).isEqualTo("com.fasterxml.jackson.core:jackson-core");
                  assertThat(row.getStatus1()).isEqualTo("200");
                  assertThat(row.getHttpResponseCount()).isGreaterThanOrEqualTo(1);
                  assertThat(row.getConnectionFailureCount()).isZero();
                  assertThat(row.getColdStartMs()).isNotNull();
                  assertThat(row.getLatencyDecile5Ms()).isNotNull();
                  // Fixed 10-column schema padded with empty statuses when fewer requests are made.
                  assertThat(row.getStatus3()).isEmpty();
              })
            )
            .executionContext(emptySettingsContext()),
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>test</artifactId>
                  <version>0.1.0</version>
              </project>
              """
          )
        );
    }

    @Test
    void unreachableRepositoryIsClassified() {
        rewriteRun(
          spec -> spec
            .dataTable(RepositoryResponseLatency.Row.class, rows ->
              assertThat(rows)
                .filteredOn(row -> "https://nonexistent.moderne.io/maven2".equals(row.getRepositoryUri()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getHttpResponseCount()).isZero();
                    assertThat(row.getConnectionFailureCount()).isEqualTo(2);
                    assertThat(row.getStatus1()).isEqualTo("UnknownHostException");
                    assertThat(row.getLatencyDecile5Ms()).isNull();
                    assertThat(row.getProbableBottleneck()).isEqualTo("UNREACHABLE");
                    assertThat(row.getNotes()).contains("UnknownHostException");
                })
            )
            .executionContext(emptySettingsContext()),
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>test</artifactId>
                  <version>0.1.0</version>
                  <repositories>
                      <repository>
                          <id>nonexistent</id>
                          <url>https://nonexistent.moderne.io/maven2</url>
                      </repository>
                  </repositories>
              </project>
              """
          )
        );
    }

    @Test
    void readsServerTimingHeader() {
        // A stub HttpSender lets us assert the Server-Timing parsing deterministically: the real gateway/connector
        // only emit the header in a deployed tunnel, and Maven Central never does.
        HttpSender stub = request -> new HttpSender.Response(200, new ByteArrayInputStream("<metadata/>".getBytes()),
          Map.of(
            "Server-Timing", List.of("repo;dur=250, rsocket;dur=40"),
            "Server", List.of("StubServer")),
          () -> {
          });

        rewriteRun(
          spec -> {
              MavenExecutionContextView ctx = emptySettingsContext();
              HttpSenderExecutionContextView.view(ctx).setHttpSender(stub);
              spec
                .dataTable(RepositoryResponseLatency.Row.class, rows ->
                  assertThat(rows)
                    .filteredOn(row -> "https://stub.moderne.io/maven2".equals(row.getRepositoryUri()))
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.getStatus1()).isEqualTo("200");
                        assertThat(row.getHttpResponseCount()).isEqualTo(2);
                        assertThat(row.getServerTimingRepositoryMs()).isEqualTo(250L);
                        assertThat(row.getServerTimingRsocketMs()).isEqualTo(40L);
                        assertThat(row.getUpstreamServer()).contains("StubServer");
                        assertThat(row.getColdStartMs()).isNotNull();
                    }))
                .executionContext(ctx);
          },
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>test</artifactId>
                  <version>0.1.0</version>
                  <repositories>
                      <repository>
                          <id>stub</id>
                          <url>https://stub.moderne.io/maven2</url>
                      </repository>
                  </repositories>
              </project>
              """
          )
        );
    }

    @Test
    void configurableArtifact() {
        rewriteRun(
          spec -> spec
            .recipe(new RepositoryLatencyDiagnostic("com.google.guava", "guava", 2))
            .dataTable(RepositoryResponseLatency.Row.class, rows ->
              assertThat(rows).anySatisfy(row -> {
                  assertThat(row.getRepositoryUri()).isEqualTo("https://repo.maven.apache.org/maven2");
                  assertThat(row.getGroupArtifact()).isEqualTo("com.google.guava:guava");
                  assertThat(row.getMetadataUri())
                    .isEqualTo("https://repo.maven.apache.org/maven2/com/google/guava/guava/maven-metadata.xml");
              })
            )
            .executionContext(emptySettingsContext()),
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>test</artifactId>
                  <version>0.1.0</version>
              </project>
              """
          )
        );
    }
}
