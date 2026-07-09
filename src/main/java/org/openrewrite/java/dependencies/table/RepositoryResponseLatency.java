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
package org.openrewrite.java.dependencies.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class RepositoryResponseLatency extends DataTable<RepositoryResponseLatency.Row> {

    public RepositoryResponseLatency(Recipe recipe) {
        super(recipe,
                "Repository response latency",
                "One row per effective artifact repository, recording the latency of repeatedly fetching a Maven " +
                "metadata file. Because the recipe only observes end-to-end time, the derived columns attribute latency " +
                "to a layer (Moderne infrastructure vs. the repository) as best they can from status codes, the connector " +
                "cache hit/miss cliff, and any Server-Timing header returned by the tunnel.");
    }

    @Value
    @Builder
    public static class Row {
        @Column(displayName = "Repository URI",
                description = "The effective (post-mirror, normalized) URI of the repository.")
        String repositoryUri;

        @Column(displayName = "Maven metadata URI",
                description = "The exact `maven-metadata.xml` URL that was probed.")
        String metadataUri;

        @Column(displayName = "Group:artifact probed",
                description = "The `groupId:artifactId` whose metadata was requested.")
        String groupArtifact;

        @Column(displayName = "HTTP response count",
                description = "How many of the probes returned any HTTP status (2xx-5xx). The remainder failed at the " +
                              "transport layer (timeout, unknown host, connection refused).")
        Integer httpResponseCount;

        @Column(displayName = "Connection failure count",
                description = "How many probes failed before receiving an HTTP status (timeout, unknown host, refused).")
        Integer connectionFailureCount;

        @Column(displayName = "Request 1 status", description = "HTTP status, or the transport exception, of probe 1.")
        String status1;
        @Column(displayName = "Request 2 status", description = "HTTP status, or the transport exception, of probe 2.")
        String status2;
        @Column(displayName = "Request 3 status", description = "HTTP status, or the transport exception, of probe 3.")
        String status3;
        @Column(displayName = "Request 4 status", description = "HTTP status, or the transport exception, of probe 4.")
        String status4;
        @Column(displayName = "Request 5 status", description = "HTTP status, or the transport exception, of probe 5.")
        String status5;
        @Column(displayName = "Request 6 status", description = "HTTP status, or the transport exception, of probe 6.")
        String status6;
        @Column(displayName = "Request 7 status", description = "HTTP status, or the transport exception, of probe 7.")
        String status7;
        @Column(displayName = "Request 8 status", description = "HTTP status, or the transport exception, of probe 8.")
        String status8;
        @Column(displayName = "Request 9 status", description = "HTTP status, or the transport exception, of probe 9.")
        String status9;
        @Column(displayName = "Request 10 status", description = "HTTP status, or the transport exception, of probe 10.")
        String status10;

        @Column(displayName = "Latency decile 1 (ms)", description = "10th percentile latency of the responding probes (fastest).")
        @Nullable
        Long latencyDecile1Ms;
        @Column(displayName = "Latency decile 2 (ms)", description = "20th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile2Ms;
        @Column(displayName = "Latency decile 3 (ms)", description = "30th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile3Ms;
        @Column(displayName = "Latency decile 4 (ms)", description = "40th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile4Ms;
        @Column(displayName = "Latency decile 5 (ms)", description = "50th percentile (median) latency of the responding probes.")
        @Nullable
        Long latencyDecile5Ms;
        @Column(displayName = "Latency decile 6 (ms)", description = "60th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile6Ms;
        @Column(displayName = "Latency decile 7 (ms)", description = "70th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile7Ms;
        @Column(displayName = "Latency decile 8 (ms)", description = "80th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile8Ms;
        @Column(displayName = "Latency decile 9 (ms)", description = "90th percentile latency of the responding probes.")
        @Nullable
        Long latencyDecile9Ms;
        @Column(displayName = "Latency decile 10 (ms)", description = "100th percentile latency of the responding probes (slowest).")
        @Nullable
        Long latencyDecile10Ms;

        @Column(displayName = "Cold start latency (ms)",
                description = "Latency of the first counted probe. Includes connection setup and, when the connector " +
                              "cache is cold, the full connector-to-repository fetch.")
        @Nullable
        Long coldStartMs;

        @Column(displayName = "Warm median latency (ms)",
                description = "Median latency of probes 2..N. When the connector caches metadata these are cache hits, " +
                              "so this approximates the Moderne-internal round trip (worker->gateway->connector) with no " +
                              "repository fetch.")
        @Nullable
        Long warmMedianMs;

        @Column(displayName = "Cache-immune median latency (ms)",
                description = "Median latency of the cache-busting probes (a unique query string forces the connector to " +
                              "reach the repository every time), i.e. the true full-path latency.")
        @Nullable
        Long cacheImmuneMedianMs;

        @Column(displayName = "Estimated connector->repository latency (ms)",
                description = "Cache-immune median minus warm median. When the warm probes are connector cache hits, this " +
                              "isolates the connector-to-repository hop. Meaningless (near zero) when the connector is not " +
                              "caching — see the `Warm requests served from connector cache` column.")
        @Nullable
        Long estimatedRepoLatencyMs;

        @Column(displayName = "Latency jitter p90-p10 (ms)",
                description = "Spread between the 90th and 10th percentile of the responding probes. High jitter points to " +
                              "intermittent causes (GC, RSocket head-of-line blocking, connector reconnects, CDN variance).")
        @Nullable
        Long jitterMs;

        @Column(displayName = "Warm requests served from connector cache",
                description = "True when the warm probes returned quickly with empty response headers, the signature of a " +
                              "connector-side Maven cache hit. When false, every probe is a full-path request.")
        @Nullable
        Boolean warmServedFromConnectorCache;

        @Column(displayName = "Server-Timing repository (ms)",
                description = "Connector-measured time spent fetching from the repository, read from the `Server-Timing` " +
                              "response header. Empty until the gateway/connector emit it.")
        @Nullable
        Long serverTimingRepositoryMs;

        @Column(displayName = "Server-Timing connector (ms)",
                description = "Connector-measured in-connector processing time, read from the `Server-Timing` response header. " +
                              "Empty until the gateway/connector emit it.")
        @Nullable
        Long serverTimingConnectorMs;

        @Column(displayName = "Server-Timing RSocket (ms)",
                description = "Gateway-measured gateway<->connector RSocket exchange time, read from the `Server-Timing` " +
                              "response header. Empty until the gateway/connector emit it.")
        @Nullable
        Long serverTimingRsocketMs;

        @Column(displayName = "Upstream server header",
                description = "The `Server`/`Via`/`X-Cache` response headers of a cache-immune probe, revealing a CDN or " +
                              "proxy in front of the repository. Empty for connector cache hits (which strip headers).")
        String upstreamServer;

        @Column(displayName = "Probable bottleneck",
                description = "A heuristic classification of where the latency or failure most likely originates.")
        String probableBottleneck;

        @Column(displayName = "Notes",
                description = "Free-form detail: transport exceptions, whether a mirror was applied, unreachable reasons.")
        String notes;
    }
}
