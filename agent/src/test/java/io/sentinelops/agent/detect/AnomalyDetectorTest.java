package io.sentinelops.agent.detect;

import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.memory.IncidentSignature;
import io.sentinelops.agent.orchestrator.AgentProperties;
import io.sentinelops.agent.prometheus.model.Sample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    private final AgentProperties props =
            new AgentProperties("sentinelops-demo", "sentinelops-demo", false, false, 20, 0.10, 1.0, 0, 4, "rule");

    private static Sample sample(String app, double value) {
        return new Sample(Map.of("app", app), value, Instant.EPOCH);
    }

    /** Fake instant-query keyed by a substring of the PromQL. */
    private Function<String, List<Sample>> query(Map<String, List<Sample>> byFragment) {
        return promql -> byFragment.entrySet().stream()
                .filter(e -> promql.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(List.of());
    }

    @Test
    void flagsErrorRateOverThreshold() {
        var detector = new AnomalyDetector(query(Map.of(
                "status=~\"5..\"", List.of(sample("order-service", 4.0)),   // 4 err/s
                "seconds_count{namespace=\"sentinelops-demo\"}[1m]", List.of(sample("order-service", 10.0)) // 10 req/s -> 40%
        )), props);

        List<Anomaly> anomalies = detector.detectFromMetrics();

        assertThat(anomalies).anySatisfy(a -> {
            assertThat(a.service()).isEqualTo("order-service");
            assertThat(a.type()).isEqualTo(Anomaly.Type.ERROR_RATE);
        });
    }

    @Test
    void flagsLatencyOverThreshold() {
        var detector = new AnomalyDetector(query(Map.of(
                "seconds_count{namespace=\"sentinelops-demo\"}[1m]", List.of(sample("inventory-service", 5.0)),
                "histogram_quantile", List.of(sample("inventory-service", 3.5)) // p95 3.5s > 1s
        )), props);

        List<Anomaly> anomalies = detector.detectFromMetrics();

        assertThat(anomalies).anySatisfy(a -> {
            assertThat(a.service()).isEqualTo("inventory-service");
            assertThat(a.type()).isEqualTo(Anomaly.Type.LATENCY);
        });
    }

    @Test
    void degradedDeploymentSurfacesAsCrash() {
        var detector = new AnomalyDetector(promql -> List.of(), props);
        ClusterState state = new ClusterState("sentinelops-demo", List.of(
                new ClusterState.DeploymentState("inventory-service", 1, 0, List.of())));

        List<Anomaly> anomalies = detector.detectAvailability(state);

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).type()).isEqualTo(Anomaly.Type.CRASH);
    }

    @Test
    void signatureFactoryMapsLatencyToNetwork() {
        Anomaly latency = new Anomaly("inventory-service", Anomaly.Type.LATENCY, 3, 1, Map.of(), "p95 3s");
        IncidentSignature sig = SignatureFactory.from(latency);
        assertThat(sig.symptomType()).isEqualTo("latency");
        assertThat(sig.errorPatternCategory()).isEqualTo("network-latency");
        assertThat(sig.serviceTypes()).contains("network");
    }
}
