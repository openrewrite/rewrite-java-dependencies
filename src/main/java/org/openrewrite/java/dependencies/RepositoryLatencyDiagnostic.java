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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.java.dependencies.table.RepositoryResponseLatency;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenRepositoryMirror;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyMap;
import static org.openrewrite.internal.StringUtils.isBlank;

@EqualsAndHashCode(callSuper = false)
@Value
public class RepositoryLatencyDiagnostic extends ScanningRecipe<RepositoryLatencyDiagnostic.Accumulator> {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern SERVER_TIMING_DUR = Pattern.compile("dur=([0-9.]+)");
    // Latency thresholds (ms) used only for the heuristic bottleneck classification.
    private static final long HEALTHY_MS = 500;
    private static final long SLOW_MS = 2000;

    transient RepositoryResponseLatency latencies = new RepositoryResponseLatency(this);

    String displayName = "Artifact repository latency diagnostic";

    String description = "Measures how long it takes to fetch a Maven metadata file from every artifact repository " +
                         "known to the build, to help diagnose slow or flaky repository connectivity. For each effective " +
                         "repository it requests `maven-metadata.xml` for a configurable artifact several times and records " +
                         "the HTTP status of each request and the latency deciles in the `Repository response latency` data " +
                         "table. \n\n" +
                         "Requests are issued through the same network path a real recipe run uses, so in a Moderne " +
                         "deployment they traverse the worker, gateway and connector before reaching the repository. The " +
                         "recipe only observes end-to-end time, so it additionally fires cache-busting requests and reads " +
                         "any `Server-Timing` response header to attribute latency to the repository versus Moderne " +
                         "infrastructure as far as the available signals allow.";

    @Option(displayName = "Group ID",
            description = "The group ID of the artifact whose `maven-metadata.xml` is fetched. " +
                          "Default value is \"com.fasterxml.jackson.core\".",
            example = "com.fasterxml.jackson.core",
            required = false)
    @Nullable
    String groupId;

    @Option(displayName = "Artifact ID",
            description = "The artifact ID of the artifact whose `maven-metadata.xml` is fetched. " +
                          "Default value is \"jackson-core\".",
            example = "jackson-core",
            required = false)
    @Nullable
    String artifactId;

    @Option(displayName = "Requests per repository",
            description = "How many times to request the metadata from each repository. Default value is 10. " +
                          "The data table always reports the first 10 request statuses and 10 latency deciles.",
            example = "10",
            required = false)
    @Nullable
    Integer requestsPerRepository;

    public static class Accumulator {
        boolean foundGradle;
        Set<MavenRepository> repositoriesFromGradle = new HashSet<>();

        boolean foundMaven;
        Set<MavenRepository> repositoriesFromMaven = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return null;
                }
                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
                    acc.foundGradle = true;
                    acc.repositoriesFromGradle.addAll(gp.getMavenRepositories());
                    acc.repositoriesFromGradle.addAll(gp.getMavenPluginRepositories());
                });
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                    acc.foundMaven = true;
                    acc.repositoriesFromMaven.addAll(mrr.getPom().getRepositories());
                });
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Set<String> seen = new HashSet<>();
        if (acc.foundMaven) {
            probeAll(true, acc.repositoriesFromMaven, seen, ctx);
        }
        if (acc.foundGradle) {
            probeAll(false, acc.repositoriesFromGradle, seen, ctx);
        }
        return Collections.emptyList();
    }

    private void probeAll(boolean addMavenCentral, Collection<MavenRepository> repos, Set<String> seen, ExecutionContext ctx) {
        Collection<MavenRepository> effectiveRepos = repos;
        if (addMavenCentral && !effectiveRepos.contains(MavenRepository.MAVEN_CENTRAL)) {
            effectiveRepos = new ArrayList<>(effectiveRepos);
            effectiveRepos.add(MavenRepository.MAVEN_CENTRAL);
        }

        HttpSender httpSender = HttpSenderExecutionContextView.view(ctx).getHttpSender();
        MavenExecutionContextView mctx = MavenExecutionContextView.view(ctx);
        MavenPomDownloader mpd = new MavenPomDownloader(ctx);

        for (MavenRepository repo : effectiveRepos) {
            MavenRepository target = mpd.normalizeRepository(repo, mctx, null);
            String note = "";
            if (target == null) {
                MavenSettings settings = mctx.getSettings();
                target = settings == null ? repo : MavenRepositoryMirror.apply(mctx.getMirrors(settings), repo);
                note = "Repository root did not respond to a ping; probed anyway. ";
            }
            // Local repositories are on disk, not a network endpoint, so latency is not meaningful.
            if (target.getUri().startsWith("file:")) {
                continue;
            }
            if (seen.add(noTrailingSlash(target.getUri()))) {
                latencies.insertRow(ctx, probeRepository(httpSender, target, note));
            }
        }
    }

    private RepositoryResponseLatency.Row probeRepository(HttpSender httpSender, MavenRepository repo, String note) {
        String group = isBlank(groupId) ? "com.fasterxml.jackson.core" : groupId;
        String artifact = isBlank(artifactId) ? "jackson-core" : artifactId;
        int n = requestsPerRepository == null ? 10 : Math.max(1, requestsPerRepository);

        String base = repo.getUri().endsWith("/") ? repo.getUri() : repo.getUri() + "/";
        String metadataUrl = base + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml";

        // One uncounted warm-up pays connection/proxy/certificate setup and primes any connector cache.
        send(httpSender, repo, metadataUrl);

        List<Probe> plain = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        List<Long> respondedLatencies = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Probe p = send(httpSender, repo, metadataUrl);
            plain.add(p);
            statuses.add(p.label);
            if (p.responded) {
                respondedLatencies.add(p.millis);
            }
        }

        // Cache-busting probes: a unique query string makes the connector cache miss and reach the repository every time.
        long nonce = System.nanoTime();
        List<Long> cacheImmuneLatencies = new ArrayList<>();
        Probe immuneWithHeaders = null;
        for (int i = 0; i < n; i++) {
            String busted = metadataUrl + "?mrnLatencyProbe=" + nonce + "-" + i;
            Probe p = send(httpSender, repo, busted);
            if (p.responded) {
                cacheImmuneLatencies.add(p.millis);
            }
            if (immuneWithHeaders == null && p.responded && !p.emptyHeaders) {
                immuneWithHeaders = p;
            }
        }

        List<Long> sorted = new ArrayList<>(respondedLatencies);
        Collections.sort(sorted);

        int responded = respondedLatencies.size();
        Long coldStartMs = plain.get(0).responded ? plain.get(0).millis : null;
        List<Long> warm = new ArrayList<>();
        int warmSuccesses = 0;
        int warmCacheHits = 0;
        for (int i = 1; i < plain.size(); i++) {
            Probe p = plain.get(i);
            if (p.responded) {
                warm.add(p.millis);
                if (p.code != null && p.code < 400) {
                    warmSuccesses++;
                    if (!repoFetched(p)) {
                        warmCacheHits++;
                    }
                }
            }
        }
        Long warmMedianMs = median(warm);
        Long cacheImmuneMedianMs = median(cacheImmuneLatencies);
        Long estimatedRepoLatencyMs = warmMedianMs != null && cacheImmuneMedianMs != null ?
                Math.max(0, cacheImmuneMedianMs - warmMedianMs) : null;
        Boolean warmServedFromCache = warmSuccesses == 0 ? null : warmCacheHits == warmSuccesses;

        Long p10 = percentile(sorted, 10);
        Long p90 = percentile(sorted, 90);
        Long jitterMs = p10 != null && p90 != null ? p90 - p10 : null;

        Map<String, List<String>> immuneHeaders = immuneWithHeaders == null ? emptyMap() : immuneWithHeaders.headers;
        Long stRepo = serverTiming(immuneHeaders, "repo");
        Long stConnector = serverTiming(immuneHeaders, "connector");
        Long stRsocket = serverTiming(immuneHeaders, "rsocket");
        String upstreamServer = upstreamServer(immuneHeaders);

        String bottleneck = classify(statuses, responded, warmMedianMs, cacheImmuneMedianMs,
                estimatedRepoLatencyMs, p10, p90, Boolean.TRUE.equals(warmServedFromCache), stRepo, stRsocket);

        return RepositoryResponseLatency.Row.builder()
                .repositoryUri(noTrailingSlash(repo.getUri()))
                .metadataUri(metadataUrl)
                .groupArtifact(group + ":" + artifact)
                .httpResponseCount(responded)
                .connectionFailureCount(n - responded)
                .status1(status(statuses, 0))
                .status2(status(statuses, 1))
                .status3(status(statuses, 2))
                .status4(status(statuses, 3))
                .status5(status(statuses, 4))
                .status6(status(statuses, 5))
                .status7(status(statuses, 6))
                .status8(status(statuses, 7))
                .status9(status(statuses, 8))
                .status10(status(statuses, 9))
                .latencyDecile1Ms(percentile(sorted, 10))
                .latencyDecile2Ms(percentile(sorted, 20))
                .latencyDecile3Ms(percentile(sorted, 30))
                .latencyDecile4Ms(percentile(sorted, 40))
                .latencyDecile5Ms(percentile(sorted, 50))
                .latencyDecile6Ms(percentile(sorted, 60))
                .latencyDecile7Ms(percentile(sorted, 70))
                .latencyDecile8Ms(percentile(sorted, 80))
                .latencyDecile9Ms(percentile(sorted, 90))
                .latencyDecile10Ms(percentile(sorted, 100))
                .coldStartMs(coldStartMs)
                .warmMedianMs(warmMedianMs)
                .cacheImmuneMedianMs(cacheImmuneMedianMs)
                .estimatedRepoLatencyMs(estimatedRepoLatencyMs)
                .jitterMs(jitterMs)
                .warmServedFromConnectorCache(warmServedFromCache)
                .serverTimingRepositoryMs(stRepo)
                .serverTimingConnectorMs(stConnector)
                .serverTimingRsocketMs(stRsocket)
                .upstreamServer(upstreamServer)
                .probableBottleneck(bottleneck)
                .notes(note + distinctFailures(statuses))
                .build();
    }

    private static Probe send(HttpSender httpSender, MavenRepository repo, String url) {
        HttpSender.Request request = httpSender.get(url)
                .withBasicAuthentication(repo.getUsername(), repo.getPassword())
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT)
                .build();
        long start = System.nanoTime();
        try (HttpSender.Response response = httpSender.send(request)) {
            int code = response.getCode();
            Map<String, List<String>> headers = response.getHeaders();
            response.getBodyAsBytes(); // fully read the body so transfer time is included
            long millis = (System.nanoTime() - start) / 1_000_000;
            boolean empty = headers == null || headers.isEmpty();
            return new Probe(true, code, Integer.toString(code), millis, empty, headers == null ? emptyMap() : headers);
        } catch (Throwable t) {
            long millis = (System.nanoTime() - start) / 1_000_000;
            Throwable cause = t;
            while (cause instanceof UncheckedIOException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            return new Probe(false, null, cause.getClass().getSimpleName(), millis, true, emptyMap());
        }
    }

    private static String classify(List<String> statuses, int responded, @Nullable Long warmMedian,
                                   @Nullable Long cacheImmuneMedian, @Nullable Long estimatedRepo,
                                   @Nullable Long p10, @Nullable Long p90, boolean warmCache,
                                   @Nullable Long stRepo, @Nullable Long stRsocket) {
        if (statuses.contains("503")) {
            return "MODERNE_INFRA_NO_CONNECTOR";
        }
        if (statuses.contains("502")) {
            return "MODERNE_TUNNEL_ERROR";
        }
        if (statuses.contains("401") || statuses.contains("403")) {
            return "REPO_AUTH";
        }
        if (statuses.contains("429")) {
            return "REPO_RATE_LIMIT";
        }
        if (responded == 0) {
            return "UNREACHABLE";
        }
        // When the tunnel reports a real per-hop breakdown, trust it over the heuristics.
        if (stRepo != null || stRsocket != null) {
            long repo = stRepo == null ? 0 : stRepo;
            long rsocket = stRsocket == null ? 0 : stRsocket;
            if (repo > rsocket && repo > SLOW_MS) {
                return "REPO_FETCH_SLOW";
            }
            if (rsocket > repo && rsocket > SLOW_MS) {
                return "INTERNAL_TRANSPORT_SLOW";
            }
        }
        if (warmCache && estimatedRepo != null && estimatedRepo > SLOW_MS) {
            return "REPO_FETCH_SLOW";
        }
        if (warmMedian != null && warmMedian > SLOW_MS) {
            return "INTERNAL_TRANSPORT_SLOW";
        }
        boolean jittery = p10 != null && p90 != null && p90 > Math.max(HEALTHY_MS, p10 * 3);
        if (jittery) {
            return "INTERMITTENT";
        }
        if (p90 != null && p90 > SLOW_MS) {
            return cacheImmuneMedian != null && warmMedian != null && cacheImmuneMedian > warmMedian * 2 ?
                    "REPO_FETCH_SLOW" : "SLOW";
        }
        return "HEALTHY";
    }

    private static String status(List<String> statuses, int i) {
        return i < statuses.size() ? statuses.get(i) : "";
    }

    private static String distinctFailures(List<String> statuses) {
        Set<String> failures = new LinkedHashSet<>();
        for (String s : statuses) {
            if (!s.isEmpty() && !s.chars().allMatch(Character::isDigit)) {
                failures.add(s);
            }
        }
        return failures.isEmpty() ? "" : "Transport failures: " + String.join(", ", failures);
    }

    private static @Nullable Long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return percentile(sorted, 50);
    }

    private static @Nullable Long percentile(List<Long> sortedAsc, int p) {
        if (sortedAsc.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(p / 100.0 * sortedAsc.size());
        int idx = Math.min(Math.max(rank - 1, 0), sortedAsc.size() - 1);
        return sortedAsc.get(idx);
    }

    private static @Nullable Long serverTiming(Map<String, List<String>> headers, String metric) {
        String value = firstHeader(headers, "Server-Timing");
        if (value == null) {
            return null;
        }
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            String name = trimmed.split(";", 2)[0].trim();
            if (name.equalsIgnoreCase(metric)) {
                Matcher m = SERVER_TIMING_DUR.matcher(trimmed);
                if (m.find()) {
                    return Math.round(Double.parseDouble(m.group(1)));
                }
            }
        }
        return null;
    }

    private static String upstreamServer(Map<String, List<String>> headers) {
        List<String> parts = new ArrayList<>();
        for (String name : new String[]{"Server", "Via", "X-Cache"}) {
            String value = firstHeader(headers, name);
            if (value != null) {
                parts.add(name + ": " + value);
            }
        }
        return String.join("; ", parts);
    }

    /**
     * Whether a probe actually reached the repository (versus being served from the connector cache). Robust across
     * deployments: a {@code repo;dur} Server-Timing means the repo was fetched; a Server-Timing with no {@code repo;dur}
     * (just {@code tunnel;dur}) means an internal-only round trip; otherwise fall back to header emptiness, since the
     * connector returns empty headers for a cache hit but the repository's own headers for a real fetch.
     */
    private static boolean repoFetched(Probe p) {
        if (serverTiming(p.headers, "repo") != null) {
            return true;
        }
        if (firstHeader(p.headers, "Server-Timing") != null) {
            return false;
        }
        return !p.emptyHeaders;
    }

    private static @Nullable String firstHeader(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (name.equalsIgnoreCase(e.getKey()) && e.getValue() != null && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    private static String noTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    @Value
    private static class Probe {
        boolean responded;
        @Nullable
        Integer code;
        String label;
        long millis;
        boolean emptyHeaders;
        Map<String, List<String>> headers;
    }
}
