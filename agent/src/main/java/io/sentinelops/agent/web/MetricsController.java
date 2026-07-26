package io.sentinelops.agent.web;

import io.sentinelops.agent.prometheus.PrometheusClient;
import io.sentinelops.agent.prometheus.model.Sample;
import io.sentinelops.agent.prometheus.model.Series;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP surface over the Prometheus client so Phase 3 is demonstrable on its
 * own (before the full agent loop exists). Also hosts a few ready-made PromQL
 * queries for the demo services.
 */
@RestController
public class MetricsController {

    private static final String NS = "sentinelops-demo";

    private final PrometheusClient prometheus;

    public MetricsController(PrometheusClient prometheus) {
        this.prometheus = prometheus;
    }

    @GetMapping("/api/prometheus/reachable")
    public Map<String, Object> reachable() {
        return Map.of("url", "prometheus", "reachable", prometheus.isReachable());
    }

    /** Passthrough instant query — e.g. /api/prometheus/query?q=up */
    @GetMapping("/api/prometheus/query")
    public List<Sample> query(@RequestParam("q") String promql) {
        return prometheus.query(promql);
    }

    /** Range query — /api/prometheus/query_range?q=...&minutes=15&stepSeconds=15 */
    @GetMapping("/api/prometheus/query_range")
    public List<Series> queryRange(@RequestParam("q") String promql,
                                   @RequestParam(defaultValue = "15") int minutes,
                                   @RequestParam(defaultValue = "15") int stepSeconds) {
        Instant end = Instant.now();
        return prometheus.queryRange(promql, end.minus(Duration.ofMinutes(minutes)), end,
                Duration.ofSeconds(stepSeconds));
    }

    /** Per-service request rate (req/s) over the last minute. */
    @GetMapping("/api/prometheus/demo/request-rate")
    public List<Sample> requestRate() {
        return prometheus.query(
                "sum by (app) (rate(http_server_requests_seconds_count{namespace=\"" + NS + "\"}[1m]))");
    }

    /** Per-service 5xx error rate (req/s) over the last minute. */
    @GetMapping("/api/prometheus/demo/error-rate")
    public List<Sample> errorRate() {
        return prometheus.query(
                "sum by (app) (rate(http_server_requests_seconds_count{namespace=\"" + NS + "\",status=~\"5..\"}[1m]))");
    }
}
