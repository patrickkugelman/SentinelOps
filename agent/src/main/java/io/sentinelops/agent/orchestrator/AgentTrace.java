package io.sentinelops.agent.orchestrator;

import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.memory.IncidentSignature;
import io.sentinelops.agent.memory.ScoredIncident;
import io.sentinelops.agent.remediate.RemediationDecision;
import io.sentinelops.agent.remediate.RemediationExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A full record of one incident-response cycle. Streamed to the dashboard as a
 * reasoning trace (Phase 7); here it's built up step-by-step and returned as JSON.
 */
public class AgentTrace {

    public record Step(Instant at, String phase, String message) { }

    public record PrecedentView(String id, String title, String sourceUrl, double score,
                                String symptomType, String errorPatternCategory) {
        static PrecedentView of(ScoredIncident s) {
            return new PrecedentView(s.incident().id(), s.incident().title(), s.incident().sourceUrl(),
                    round(s.score()), s.incident().symptomType(), s.incident().errorPatternCategory());
        }
        private static double round(double d) { return Math.round(d * 1000.0) / 1000.0; }
    }

    private final String id = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();
    private final boolean dryRun;
    private final String planner;
    private final List<Step> steps = new ArrayList<>();

    private Anomaly anomaly;
    private IncidentSignature signature;
    private List<PrecedentView> precedents = List.of();
    private RemediationDecision decision;
    private RemediationExecutor.Result result;
    private Map<String, Double> beforeMetrics;
    private Map<String, Double> afterMetrics;
    private Boolean recovered;
    private String recordedIncidentId;
    private boolean finished;

    public AgentTrace(boolean dryRun, String planner) {
        this.dryRun = dryRun;
        this.planner = planner;
    }

    public AgentTrace step(String phase, String message) {
        steps.add(new Step(Instant.now(), phase, message));
        return this;
    }

    // ---- mutators used by the orchestrator ----
    public void setAnomaly(Anomaly a) { this.anomaly = a; }
    public void setSignature(IncidentSignature s) { this.signature = s; }
    public void setPrecedents(List<ScoredIncident> p) { this.precedents = p.stream().map(PrecedentView::of).toList(); }
    public void setDecision(RemediationDecision d) { this.decision = d; }
    public void setResult(RemediationExecutor.Result r) { this.result = r; }
    public void setBeforeMetrics(Map<String, Double> m) { this.beforeMetrics = m; }
    public void setAfterMetrics(Map<String, Double> m) { this.afterMetrics = m; }
    public void setRecovered(Boolean r) { this.recovered = r; }
    public void setRecordedIncidentId(String id) { this.recordedIncidentId = id; }
    public void finish() { this.finished = true; }

    // ---- getters for JSON serialization ----
    public String getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public boolean isDryRun() { return dryRun; }
    public String getPlanner() { return planner; }
    public List<Step> getSteps() { return steps; }
    public Anomaly getAnomaly() { return anomaly; }
    public IncidentSignature getSignature() { return signature; }
    public List<PrecedentView> getPrecedents() { return precedents; }
    public RemediationDecision getDecision() { return decision; }
    public RemediationExecutor.Result getResult() { return result; }
    public Map<String, Double> getBeforeMetrics() { return beforeMetrics; }
    public Map<String, Double> getAfterMetrics() { return afterMetrics; }
    public Boolean getRecovered() { return recovered; }
    public String getRecordedIncidentId() { return recordedIncidentId; }
    public boolean isFinished() { return finished; }
}
