package io.sentinelops.agent.remediate;

import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.memory.Incident;
import io.sentinelops.agent.memory.ScoredIncident;
import io.sentinelops.agent.orchestrator.AgentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPlannerTest {

    private final AgentProperties props =
            new AgentProperties("sentinelops-demo", "sentinelops-demo", false, false, 20, 0.10, 1.0, 0, 4, "rule");
    private final RuleBasedPlanner planner = new RuleBasedPlanner(props);

    private static ScoredIncident precedent(String id, String category) {
        Incident inc = new Incident(id, "postmortem", "Title " + id, null,
                List.of("svc"), List.of("proxy"), List.of("symptom"), "error-rate",
                List.of("sig"), category, "root cause", "the fix that worked", "https://example.com/" + id);
        return new ScoredIncident(inc, 0.9, 0.6, 1, 1, 0.5);
    }

    @Test
    void rollsBackWhenNearestPrecedentIsABadChange() {
        Anomaly anomaly = new Anomaly("order-service", Anomaly.Type.ERROR_RATE, 0.4, 0.1, Map.of(), "order 5xx 40%");
        RemediationDecision d = planner.plan(anomaly, List.of(precedent("p1", "bad-deployment")), null);

        assertThat(d.action()).isEqualTo(RemediationAction.ROLLBACK);
        assertThat(d.targetService()).isEqualTo("order-service");
        assertThat(d.precedentId()).isEqualTo("p1");
        assertThat(d.justification()).contains("p1").contains("the fix that worked");
    }

    @Test
    void scalesOnResourceExhaustion() {
        Anomaly anomaly = new Anomaly("payment-service", Anomaly.Type.RESOURCE_EXHAUSTION, 0.9, 0.5, Map.of(), "cpu high");
        ClusterState state = new ClusterState("sentinelops-demo",
                List.of(new ClusterState.DeploymentState("payment-service", 1, 1, List.of())));

        RemediationDecision d = planner.plan(anomaly, List.of(precedent("p2", "cpu-saturation")), state);

        assertThat(d.action()).isEqualTo(RemediationAction.SCALE);
        assertThat(d.replicas()).isEqualTo(2);
    }

    @Test
    void restartsOnCrash() {
        Anomaly anomaly = new Anomaly("inventory-service", Anomaly.Type.CRASH, 0, 1, Map.of(), "0/1 ready");
        RemediationDecision d = planner.plan(anomaly, List.of(precedent("p3", "power-loss")), null);
        assertThat(d.action()).isEqualTo(RemediationAction.RESTART);
        assertThat(d.targetService()).isEqualTo("inventory-service");
    }

    @Test
    void noPrecedentStillProducesASymptomBasedAction() {
        Anomaly anomaly = new Anomaly("inventory-service", Anomaly.Type.LATENCY, 3.0, 1.0, Map.of(), "p95 3s");
        RemediationDecision d = planner.plan(anomaly, List.of(), null);
        assertThat(d.action()).isEqualTo(RemediationAction.RESTART);
        assertThat(d.precedentId()).isNull();
    }

    @Test
    void noAnomalyYieldsNone() {
        RemediationDecision d = planner.plan(null, List.of(), null);
        assertThat(d.action()).isEqualTo(RemediationAction.NONE);
    }
}
