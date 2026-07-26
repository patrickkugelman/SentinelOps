package io.sentinelops.agent.detect;

import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.orchestrator.AgentProperties;
import io.sentinelops.agent.prometheus.model.Sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Detects anomalies from Prometheus metrics (error-rate, p95 latency) and from
 * cluster state (degraded availability -> crash). Pure logic over an injected
 * instant-query function, so it is unit-testable without a live Prometheus.
 */
public class AnomalyDetector {

    private final Function<String, List<Sample>> instantQuery;
    private final AgentProperties props;

    public AnomalyDetector(Function<String, List<Sample>> instantQuery, AgentProperties props) {
        this.instantQuery = instantQuery;
        this.props = props;
    }

    private String ns() {
        return props.namespace();
    }

    /** All anomalies from metrics + cluster state, most severe first. */
    public List<Anomaly> detect(ClusterState clusterState) {
        List<Anomaly> anomalies = new ArrayList<>();
        anomalies.addAll(detectAvailability(clusterState));
        anomalies.addAll(detectFromMetrics());
        anomalies.sort((a, b) -> Integer.compare(a.type().ordinal(), b.type().ordinal()));
        return anomalies;
    }

    /** Degraded deployments (ready < desired) surface as CRASH anomalies. */
    public List<Anomaly> detectAvailability(ClusterState state) {
        List<Anomaly> out = new ArrayList<>();
        if (state == null) return out;
        for (ClusterState.DeploymentState d : state.deployments()) {
            if (d.degraded()) {
                out.add(new Anomaly(d.name(), Anomaly.Type.CRASH, d.readyReplicas(), d.desiredReplicas(),
                        Map.of("readyReplicas", (double) d.readyReplicas(),
                               "desiredReplicas", (double) d.desiredReplicas()),
                        "%s has %d/%d pods ready".formatted(d.name(), d.readyReplicas(), d.desiredReplicas())));
            }
        }
        return out;
    }

    /** Error-rate and latency anomalies per service from Prometheus. */
    public List<Anomaly> detectFromMetrics() {
        Map<String, Double> total = byApp("sum by (app) (rate(http_server_requests_seconds_count{namespace=\""
                + ns() + "\"}[1m]))");
        Map<String, Double> errors = byApp("sum by (app) (rate(http_server_requests_seconds_count{namespace=\""
                + ns() + "\",status=~\"5..\"}[1m]))");
        Map<String, Double> p95 = byApp("histogram_quantile(0.95, sum by (app, le) "
                + "(rate(http_server_requests_seconds_bucket{namespace=\"" + ns() + "\"}[5m])))");

        List<Anomaly> out = new ArrayList<>();
        for (var e : total.entrySet()) {
            String app = e.getKey();
            double t = e.getValue();
            double errRate = t > 0 ? errors.getOrDefault(app, 0.0) / t : 0.0;
            if (errRate > props.errorRateThreshold()) {
                out.add(new Anomaly(app, Anomaly.Type.ERROR_RATE, errRate, props.errorRateThreshold(),
                        Map.of("errorRatio", errRate, "requestRate", t),
                        "%s 5xx error ratio %.0f%%".formatted(app, errRate * 100)));
            }
        }
        for (var e : p95.entrySet()) {
            String app = e.getKey();
            double v = e.getValue();
            if (!Double.isNaN(v) && v > props.latencyP95Seconds()) {
                out.add(new Anomaly(app, Anomaly.Type.LATENCY, v, props.latencyP95Seconds(),
                        Map.of("p95Seconds", v),
                        "%s p95 latency %.2fs".formatted(app, v)));
            }
        }
        return out;
    }

    private Map<String, Double> byApp(String promql) {
        Map<String, Double> m = new HashMap<>();
        for (Sample s : instantQuery.apply(promql)) {
            String app = s.labels().get("app");
            if (app != null) m.put(app, s.value());
        }
        return m;
    }
}
