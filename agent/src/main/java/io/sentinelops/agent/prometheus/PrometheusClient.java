package io.sentinelops.agent.prometheus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentinelops.agent.prometheus.model.Sample;
import io.sentinelops.agent.prometheus.model.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client over the Prometheus HTTP API — the {@code queryPrometheus} tool.
 *
 * Exposes generic instant/range queries plus a scalar convenience; higher-level
 * anomaly PromQL lives in the decision layer (Phase 6). Deliberately dependency-
 * light: takes a pre-built {@link RestClient} so it is trivial to unit test.
 */
public class PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final RestClient http;

    public PrometheusClient(RestClient prometheusRestClient) {
        this.http = prometheusRestClient;
    }

    /** Instant query at "now". Returns one {@link Sample} per matching series. */
    public List<Sample> query(String promql) {
        PromResponse r = call("/api/v1/query?query={q}", promql);
        return toSamples(r);
    }

    /** Instant query at a specific evaluation time. */
    public List<Sample> query(String promql, Instant at) {
        PromResponse r = call("/api/v1/query?query={q}&time={t}", promql, epoch(at));
        return toSamples(r);
    }

    /** Range query — used for before/after comparisons and charts. */
    public List<Series> queryRange(String promql, Instant start, Instant end, Duration step) {
        PromResponse r = call("/api/v1/query_range?query={q}&start={s}&end={e}&step={st}",
                promql, epoch(start), epoch(end), step.toSeconds() + "s");
        return toSeries(r);
    }

    /** First sample's value, if the query returned anything. Handy for single-number checks. */
    public Optional<Double> queryScalar(String promql) {
        List<Sample> samples = query(promql);
        return samples.isEmpty() ? Optional.empty() : Optional.of(samples.get(0).value());
    }

    /** True if Prometheus answers its readiness probe. */
    public boolean isReachable() {
        try {
            http.get().uri("/-/ready").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    // ---- internals ----

    /**
     * PromQL contains braces ({@code {job="x"}}) which a UriBuilder would treat as
     * URI-template placeholders. Passing the query as a template *variable* makes
     * Spring encode it verbatim instead of re-parsing it as a template.
     */
    private PromResponse call(String uriTemplate, Object... uriVariables) {
        PromResponse resp;
        try {
            resp = http.get().uri(uriTemplate, uriVariables).retrieve().body(PromResponse.class);
        } catch (RestClientException e) {
            throw new PrometheusException("Prometheus request failed: " + e.getMessage(), e);
        }
        if (resp == null) {
            throw new PrometheusException("Prometheus returned an empty response");
        }
        if (!"success".equals(resp.status())) {
            throw new PrometheusException("Prometheus error [" + resp.errorType() + "]: " + resp.error());
        }
        return resp;
    }

    private static String epoch(Instant t) {
        // Prometheus accepts a unix timestamp (seconds, may be fractional).
        return Long.toString(t.getEpochSecond());
    }

    private static List<Sample> toSamples(PromResponse r) {
        List<Sample> out = new ArrayList<>();
        if (r.data() == null || r.data().result() == null) return out;
        for (PromResult res : r.data().result()) {
            if (res.value() == null || res.value().size() < 2) continue;
            out.add(new Sample(labels(res), parseValue(res.value().get(1)), parseTime(res.value().get(0))));
        }
        return out;
    }

    private static List<Series> toSeries(PromResponse r) {
        List<Series> out = new ArrayList<>();
        if (r.data() == null || r.data().result() == null) return out;
        for (PromResult res : r.data().result()) {
            List<Series.Point> points = new ArrayList<>();
            if (res.values() != null) {
                for (List<JsonNode> pair : res.values()) {
                    if (pair.size() < 2) continue;
                    points.add(new Series.Point(parseTime(pair.get(0)), parseValue(pair.get(1))));
                }
            }
            out.add(new Series(labels(res), points));
        }
        return out;
    }

    private static Map<String, String> labels(PromResult res) {
        return res.metric() == null ? Map.of() : res.metric();
    }

    private static Instant parseTime(JsonNode tsNode) {
        // Prometheus timestamps are float seconds since epoch.
        double seconds = tsNode.asDouble();
        return Instant.ofEpochMilli(Math.round(seconds * 1000.0));
    }

    private static double parseValue(JsonNode valNode) {
        String raw = valNode.asText();
        return switch (raw) {
            case "NaN" -> Double.NaN;
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> {
                try {
                    yield Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    log.warn("unparseable Prometheus value '{}'", raw);
                    yield Double.NaN;
                }
            }
        };
    }

    // ---- response DTOs ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromResponse(String status, String errorType, String error, PromData data) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromData(String resultType, List<PromResult> result) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromResult(Map<String, String> metric, List<JsonNode> value, List<List<JsonNode>> values) { }
}
