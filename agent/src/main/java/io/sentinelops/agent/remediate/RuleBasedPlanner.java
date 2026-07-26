package io.sentinelops.agent.remediate;

import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.memory.Incident;
import io.sentinelops.agent.memory.ScoredIncident;
import io.sentinelops.agent.orchestrator.AgentProperties;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic planner (no LLM). Crucially, the retrieved precedent INFORMS the
 * action: if the nearest precedent's failure pattern is a deployment/config
 * regression, the agent rolls back; otherwise it maps the live symptom to a
 * restart or a scale-up. Every decision carries a justification citing the
 * precedent — the project's thesis made concrete.
 */
public class RuleBasedPlanner implements RemediationPlanner {

    /** Precedent categories that point at "a bad change" -> rollback is the right move. */
    private static final Set<String> REGRESSION_CATEGORIES = Set.of(
            "bad-deployment", "config-error", "config-drift", "schema-migration");

    private final AgentProperties props;

    public RuleBasedPlanner(AgentProperties props) {
        this.props = props;
    }

    @Override
    public RemediationDecision plan(Anomaly anomaly, List<ScoredIncident> precedents, ClusterState clusterState) {
        if (anomaly == null || anomaly.service() == null) {
            return RemediationDecision.none(props.namespace(), "no actionable anomaly", name());
        }
        String service = anomaly.service();
        ScoredIncident top = precedents == null || precedents.isEmpty() ? null : precedents.get(0);
        String precedentCategory = top == null ? "" :
                String.valueOf(top.incident().errorPatternCategory()).toLowerCase(Locale.ROOT);

        RemediationAction action;
        int replicas = 0;
        String reason;

        if (top != null && REGRESSION_CATEGORIES.contains(precedentCategory)) {
            action = RemediationAction.ROLLBACK;
            reason = "the nearest precedent points at a bad change (%s), so undo the last rollout".formatted(precedentCategory);
        } else {
            switch (anomaly.type()) {
                case RESOURCE_EXHAUSTION -> {
                    action = RemediationAction.SCALE;
                    replicas = Math.min(currentReplicas(clusterState, service) + 1, props.maxReplicas());
                    reason = "resource exhaustion -> add capacity by scaling to %d replicas".formatted(replicas);
                }
                case CRASH -> {
                    action = RemediationAction.RESTART;
                    reason = "pods are unhealthy -> rolling restart to recover a clean set";
                }
                case LATENCY -> {
                    action = RemediationAction.RESTART;
                    reason = "elevated latency from the dependency -> rolling restart to shed degraded pods";
                }
                case ERROR_RATE -> {
                    action = RemediationAction.RESTART;
                    reason = "elevated error rate -> rolling restart to clear the failing state";
                }
                default -> {
                    return RemediationDecision.none(props.namespace(), "no rule matched", name());
                }
            }
        }

        String justification = buildJustification(anomaly, top, action, reason);
        return new RemediationDecision(action, service, props.namespace(), replicas, justification,
                top == null ? null : top.incident().id(),
                top == null ? null : top.incident().title(),
                top == null ? null : top.incident().sourceUrl(),
                name());
    }

    private static int currentReplicas(ClusterState state, String service) {
        if (state == null) return 1;
        return state.deployments().stream()
                .filter(d -> d.name().equals(service))
                .map(ClusterState.DeploymentState::desiredReplicas)
                .findFirst().orElse(1);
    }

    private static String buildJustification(Anomaly a, ScoredIncident top, RemediationAction action, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("Anomaly: ").append(a.summary()).append(". ");
        if (top != null) {
            Incident inc = top.incident();
            sb.append("Nearest precedent: \"").append(inc.title()).append("\" (").append(inc.id())
              .append(", score ").append(String.format(Locale.ROOT, "%.2f", top.score()))
              .append("). Its root cause pattern: ").append(inc.errorPatternCategory())
              .append("; the fix that worked: ").append(trim(inc.fix(), 240)).append(". ");
        } else {
            sb.append("No precedent retrieved. ");
        }
        sb.append("Decision: ").append(action).append(" ").append(a.service())
          .append(" — ").append(reason).append('.');
        return sb.toString();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @Override
    public String name() {
        return "rule";
    }
}
