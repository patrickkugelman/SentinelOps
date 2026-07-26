package io.sentinelops.agent.chaos;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.sentinelops.agent.chaos.ChaosDtos.ChaosHandle;
import io.sentinelops.agent.chaos.ChaosDtos.TriggerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Trigger surface for the pre-built chaos experiments (used by the dashboard). */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final ChaosService chaos;

    public ChaosController(ChaosService chaos) {
        this.chaos = chaos;
    }

    /** The catalog of experiments the dashboard renders buttons for. */
    @GetMapping("/experiments")
    public List<ExperimentDef> experiments() {
        return chaos.catalog();
    }

    /** Trigger an experiment. Body is optional: {"target": "...", "durationSeconds": 60}. */
    @PostMapping("/experiments/{id}")
    public ChaosHandle trigger(@PathVariable String id,
                               @RequestBody(required = false) TriggerRequest req) {
        return chaos.trigger(id, req);
    }

    /** List active agent-managed experiments. */
    @GetMapping("/active")
    public List<ChaosHandle> active() {
        return chaos.active();
    }

    /** Stop one experiment by name. */
    @DeleteMapping("/active/{name}")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String name) {
        boolean deleted = chaos.stop(name);
        return ResponseEntity.ok(Map.of("name", name, "deleted", deleted));
    }

    // ---- error mapping ----

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> onGuardrail(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(KubernetesClientException.class)
    public ResponseEntity<Map<String, String>> onClusterError(KubernetesClientException e) {
        // Cluster unreachable or Chaos Mesh CRDs not installed.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "kubernetes/chaos-mesh unavailable: " + e.getMessage()));
    }
}
