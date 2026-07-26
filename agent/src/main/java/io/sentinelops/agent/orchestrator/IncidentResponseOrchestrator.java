package io.sentinelops.agent.orchestrator;

import io.sentinelops.agent.cluster.ClusterOperations;
import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.detect.AnomalyDetector;
import io.sentinelops.agent.detect.SignatureFactory;
import io.sentinelops.agent.memory.Incident;
import io.sentinelops.agent.memory.IncidentMemoryService;
import io.sentinelops.agent.memory.IncidentSignature;
import io.sentinelops.agent.memory.ScoredIncident;
import io.sentinelops.agent.prometheus.PrometheusClient;
import io.sentinelops.agent.prometheus.model.Sample;
import io.sentinelops.agent.remediate.RemediationDecision;
import io.sentinelops.agent.remediate.RemediationExecutor;
import io.sentinelops.agent.remediate.RemediationPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent brain. Runs one incident-response cycle:
 * detect → build signature → retrieve precedents → reason (precedent-informed) →
 * remediate (guardrailed) → verify recovery → record the outcome back to memory.
 */
@Service
public class IncidentResponseOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IncidentResponseOrchestrator.class);
    private static final int TRACE_HISTORY = 25;

    private final ClusterOperations cluster;
    private final AnomalyDetector detector;
    private final IncidentMemoryService memory;
    private final RemediationPlanner planner;
    private final RemediationExecutor executor;
    private final PrometheusClient prometheus;
    private final AgentRuntime runtime;
    private final AgentProperties props;
    private final AgentEventBus events;

    private final Deque<AgentTrace> history = new ArrayDeque<>();

    public IncidentResponseOrchestrator(ClusterOperations cluster, IncidentMemoryService memory,
                                        RemediationPlanner planner, RemediationExecutor executor,
                                        PrometheusClient prometheus, AgentRuntime runtime,
                                        AgentProperties props, AgentEventBus events) {
        this.cluster = cluster;
        this.memory = memory;
        this.planner = planner;
        this.executor = executor;
        this.prometheus = prometheus;
        this.runtime = runtime;
        this.props = props;
        this.events = events;
        this.detector = new AnomalyDetector(prometheus::query, props);
    }

    /** Add a step to the trace AND stream the (growing) trace to dashboards. */
    private void emit(AgentTrace trace, String phase, String message) {
        trace.step(phase, message);
        events.publish("trace", trace);
    }

    public synchronized List<AgentTrace> recentTraces() {
        return List.copyOf(history);
    }

    /** Run one response cycle. Optionally force a specific service/anomaly type. */
    public AgentTrace respond(String explicitService, String explicitType, int topK) {
        AgentTrace trace = new AgentTrace(runtime.isDryRun(), planner.name());

        emit(trace, "detect", "reading cluster state and querying Prometheus");
        ClusterState state = cluster.getClusterState();

        Anomaly anomaly = selectAnomaly(state, explicitService, explicitType, trace);
        if (anomaly == null) {
            emit(trace, "done", "no anomaly detected — nothing to remediate");
            trace.finish();
            events.publish("trace", trace);
            return store(trace);
        }
        trace.setAnomaly(anomaly);
        emit(trace, "anomaly", anomaly.summary());

        IncidentSignature signature = SignatureFactory.from(anomaly);
        trace.setSignature(signature);
        emit(trace, "signature", "symptom=%s category=%s serviceTypes=%s"
                .formatted(signature.symptomType(), signature.errorPatternCategory(), signature.serviceTypes()));

        List<ScoredIncident> precedents = memory.retrieve(signature, topK > 0 ? topK : 5);
        trace.setPrecedents(precedents);
        emit(trace, "retrieve", precedents.isEmpty() ? "no precedents found"
                : "top precedent: %s (score %.2f) — %s".formatted(
                        precedents.get(0).incident().id(), precedents.get(0).score(),
                        precedents.get(0).incident().sourceUrl()));

        trace.setBeforeMetrics(metricsFor(anomaly.service()));

        RemediationDecision decision = planner.plan(anomaly, precedents, state);
        trace.setDecision(decision);
        emit(trace, "reason", decision.justification());

        // A failed remediation must not sink the whole response: record it in the
        // trace so the dashboard shows exactly what went wrong, then carry on.
        RemediationExecutor.Result result;
        try {
            result = executor.execute(decision);
        } catch (Exception e) {
            String msg = "remediation failed: " + rootMessage(e);
            log.error("Remediation failed for {}/{}: {}", decision.namespace(), decision.targetService(), msg, e);
            result = new RemediationExecutor.Result(false, runtime.isDryRun(),
                    decision.action().name(), decision.targetService(), msg);
        }
        trace.setResult(result);
        emit(trace, "remediate", result.message());

        verify(anomaly, result, trace);
        recordOutcome(anomaly, signature, decision, result, trace);

        trace.finish();
        events.publish("trace", trace);
        log.info("Response cycle complete: trace={} action={} target={} dryRun={}",
                trace.getId(), decision.action(), decision.targetService(), runtime.isDryRun());
        return store(trace);
    }

    private Anomaly selectAnomaly(ClusterState state, String explicitService, String explicitType, AgentTrace trace) {
        List<Anomaly> detected = detector.detect(state);
        if (explicitService != null && !explicitService.isBlank()) {
            Anomaly match = detected.stream().filter(a -> explicitService.equals(a.service())).findFirst().orElse(null);
            if (match != null) return match;
            if (explicitType != null && !explicitType.isBlank()) {
                emit(trace, "detect", "no live anomaly for " + explicitService + "; synthesizing " + explicitType);
                return synthesize(explicitService, explicitType);
            }
        }
        if (!detected.isEmpty()) {
            emit(trace, "detect", "detected " + detected.size() + " anomaly(ies); triaging most severe");
            return detected.get(0);
        }
        return null;
    }

    private static Anomaly synthesize(String service, String type) {
        Anomaly.Type t = switch (type.toLowerCase()) {
            case "crash", "pod-kill" -> Anomaly.Type.CRASH;
            case "error-rate", "network-partition" -> Anomaly.Type.ERROR_RATE;
            case "resource-exhaustion", "cpu", "cpu-stress" -> Anomaly.Type.RESOURCE_EXHAUSTION;
            default -> Anomaly.Type.LATENCY;
        };
        return new Anomaly(service, t, 0, 0, Map.of(), "operator-specified %s on %s".formatted(t, service));
    }

    private void verify(Anomaly anomaly, RemediationExecutor.Result result, AgentTrace trace) {
        if (props.verifySeconds() <= 0 || !result.executed()) {
            emit(trace, "verify", "skipped (dry-run or verify disabled)");
            return;
        }
        try {
            Thread.sleep(Duration.ofSeconds(props.verifySeconds()).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, Double> after = metricsFor(anomaly.service());
        trace.setAfterMetrics(after);
        Boolean recovered = improved(trace.getBeforeMetrics(), after);
        trace.setRecovered(recovered);
        emit(trace, "verify", "post-action metrics: " + after + (recovered == null ? "" : recovered ? " (recovering)" : " (not yet recovered)"));
    }

    private void recordOutcome(Anomaly anomaly, IncidentSignature signature, RemediationDecision decision,
                               RemediationExecutor.Result result, AgentTrace trace) {
        String id = "sentinelops-%s-%s-%d".formatted(anomaly.service(),
                anomaly.type().name().toLowerCase(), System.currentTimeMillis());
        String rootCause = "Detected %s on %s. Nearest precedent: %s.".formatted(
                anomaly.type(), anomaly.service(),
                decision.precedentId() == null ? "none" : decision.precedentId());
        Incident outcome = new Incident(id, "sentinelops",
                "%s on %s remediated by %s".formatted(anomaly.type(), anomaly.service(), decision.action()),
                LocalDate.now(), List.of(anomaly.service()), signature.serviceTypes(),
                List.of(anomaly.summary()), signature.symptomType(), List.of(anomaly.summary()),
                signature.errorPatternCategory(), rootCause,
                "%s — %s".formatted(decision.action(), result.message()),
                "internal://sentinelops/trace/" + trace.getId());
        try {
            memory.record(outcome);
            trace.setRecordedIncidentId(id);
            emit(trace, "record", "wrote resolved incident " + id + " back to memory");
        } catch (Exception e) {
            emit(trace, "record", "failed to record outcome: " + e.getMessage());
        }
    }

    /** error-ratio / p95 / request-rate for one service (best-effort). */
    private Map<String, Double> metricsFor(String service) {
        Map<String, Double> m = new HashMap<>();
        try {
            double total = firstValue("sum(rate(http_server_requests_seconds_count{namespace=\""
                    + props.namespace() + "\",app=\"" + service + "\"}[1m]))");
            double err = firstValue("sum(rate(http_server_requests_seconds_count{namespace=\""
                    + props.namespace() + "\",app=\"" + service + "\",status=~\"5..\"}[1m]))");
            double p95 = firstValue("histogram_quantile(0.95, sum by (le) "
                    + "(rate(http_server_requests_seconds_bucket{namespace=\"" + props.namespace()
                    + "\",app=\"" + service + "\"}[5m])))");
            m.put("requestRate", round(total));
            m.put("errorRatio", round(total > 0 ? err / total : 0.0));
            if (!Double.isNaN(p95)) m.put("p95Seconds", round(p95));
        } catch (Exception e) {
            log.debug("metrics snapshot failed for {}: {}", service, e.toString());
        }
        return m;
    }

    private double firstValue(String promql) {
        List<Sample> s = prometheus.query(promql);
        return s.isEmpty() ? 0.0 : s.get(0).value();
    }

    private static Boolean improved(Map<String, Double> before, Map<String, Double> after) {
        if (before == null || after == null) return null;
        Double be = before.get("errorRatio");
        Double ae = after.get("errorRatio");
        if (be != null && ae != null && be > 0) return ae < be;
        Double bp = before.get("p95Seconds");
        Double ap = after.get("p95Seconds");
        if (bp != null && ap != null) return ap < bp;
        return null;
    }

    private static double round(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage();
        return r.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    private synchronized AgentTrace store(AgentTrace trace) {
        history.addFirst(trace);
        while (history.size() > TRACE_HISTORY) history.removeLast();
        return trace;
    }
}
