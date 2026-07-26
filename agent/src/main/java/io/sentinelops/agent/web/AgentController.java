package io.sentinelops.agent.web;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.sentinelops.agent.cluster.ClusterOperations;
import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.orchestrator.AgentEventBus;
import io.sentinelops.agent.orchestrator.AgentProperties;
import io.sentinelops.agent.orchestrator.AgentRuntime;
import io.sentinelops.agent.orchestrator.AgentTrace;
import io.sentinelops.agent.orchestrator.IncidentResponseOrchestrator;
import io.sentinelops.agent.remediate.RemediationPlanner;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The agent's control + observability surface (used by the dashboard). */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final IncidentResponseOrchestrator orchestrator;
    private final ClusterOperations cluster;
    private final AgentRuntime runtime;
    private final AgentProperties props;
    private final RemediationPlanner planner;
    private final AgentEventBus events;

    public AgentController(IncidentResponseOrchestrator orchestrator, ClusterOperations cluster,
                           AgentRuntime runtime, AgentProperties props, RemediationPlanner planner,
                           AgentEventBus events) {
        this.orchestrator = orchestrator;
        this.cluster = cluster;
        this.runtime = runtime;
        this.props = props;
        this.planner = planner;
        this.events = events;
    }

    /** SSE stream of live reasoning-trace updates for the dashboard. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return events.subscribe();
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return Map.of(
                "namespace", props.namespace(),
                "allowedNamespace", props.allowedNamespace(),
                "dryRun", runtime.isDryRun(),
                "planner", planner.name(),
                "autoRemediate", props.autoRemediate(),
                "verifySeconds", props.verifySeconds());
    }

    @PostMapping("/dry-run")
    public Map<String, Object> setDryRun(@RequestParam boolean enabled) {
        runtime.setDryRun(enabled);
        return Map.of("dryRun", runtime.isDryRun());
    }

    /** Run one incident-response cycle. Optionally force a service + anomaly type. */
    @PostMapping("/respond")
    public AgentTrace respond(@RequestParam(required = false) String service,
                              @RequestParam(required = false) String type,
                              @RequestParam(defaultValue = "5") int topK) {
        return orchestrator.respond(service, type, topK);
    }

    @GetMapping("/traces")
    public List<AgentTrace> traces() {
        return orchestrator.recentTraces();
    }

    // ---- read tools exposed directly ----

    @GetMapping("/cluster")
    public ClusterState cluster() {
        return cluster.getClusterState();
    }

    @GetMapping("/logs")
    public Map<String, String> logs(@RequestParam String service,
                                    @RequestParam(defaultValue = "100") int lines) {
        return Map.of("service", service, "logs", cluster.getPodLogs(service, lines));
    }

    // ---- error mapping ----

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> onGuardrail(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(KubernetesClientException.class)
    public ResponseEntity<Map<String, String>> onClusterError(KubernetesClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "kubernetes unavailable: " + e.getMessage()));
    }

    /** Last resort: surface the actual cause instead of a bare 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> onUnexpected(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage())));
    }
}
