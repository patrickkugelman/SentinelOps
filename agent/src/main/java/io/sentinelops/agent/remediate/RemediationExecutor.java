package io.sentinelops.agent.remediate;

import io.sentinelops.agent.cluster.ClusterOperations;
import io.sentinelops.agent.orchestrator.AgentProperties;
import io.sentinelops.agent.orchestrator.AgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes a {@link RemediationDecision} under the safety guardrails:
 *  1. namespace allow-list — refuse anything outside the sandbox;
 *  2. justification is logged BEFORE any action;
 *  3. dry-run — reason + log but never touch the cluster.
 */
@Component
public class RemediationExecutor {

    private static final Logger log = LoggerFactory.getLogger(RemediationExecutor.class);

    private final ClusterOperations cluster;
    private final AgentProperties props;
    private final AgentRuntime runtime;

    public RemediationExecutor(ClusterOperations cluster, AgentProperties props, AgentRuntime runtime) {
        this.cluster = cluster;
        this.props = props;
        this.runtime = runtime;
    }

    public record Result(boolean executed, boolean dryRun, String action, String target, String message) { }

    public Result execute(RemediationDecision d) {
        if (d.action() == RemediationAction.NONE) {
            log.info("No remediation: {}", d.justification());
            return new Result(false, runtime.isDryRun(), "NONE", d.targetService(), d.justification());
        }

        // Guardrail 1: namespace allow-list.
        if (!props.allowedNamespace().equals(d.namespace())) {
            String msg = "refusing remediation outside allowed namespace '%s' (asked '%s')"
                    .formatted(props.allowedNamespace(), d.namespace());
            log.error("GUARDRAIL: {}", msg);
            throw new SecurityException(msg);
        }

        // Guardrail 2: justification is logged BEFORE execution, always.
        log.info("REMEDIATION PLAN [{}] {} {}/{} :: {}", d.planner(), d.action(),
                d.namespace(), d.targetService(), d.justification());

        // Guardrail 3: dry-run short-circuits any cluster mutation.
        if (runtime.isDryRun()) {
            String msg = "[DRY-RUN] would %s %s".formatted(d.action(), d.targetService());
            log.info(msg);
            return new Result(false, true, d.action().name(), d.targetService(), msg);
        }

        String msg = perform(d);
        log.info("REMEDIATION EXECUTED: {}", msg);
        return new Result(true, false, d.action().name(), d.targetService(), msg);
    }

    private String perform(RemediationDecision d) {
        return switch (d.action()) {
            case RESTART -> {
                cluster.restart(d.targetService());
                yield "restarted " + d.targetService();
            }
            case SCALE -> {
                int replicas = d.replicas() > 0 ? d.replicas() : cluster.currentReplicas(d.targetService()) + 1;
                cluster.scale(d.targetService(), replicas);
                yield "scaled " + d.targetService() + " to " + replicas + " replicas";
            }
            case ROLLBACK -> {
                // A freshly deployed service has no prior revision; rolling back
                // would fail, so fall back to a restart and say so explicitly.
                if (!cluster.hasPreviousRevision(d.targetService())) {
                    log.warn("no previous revision for {} — falling back to a rolling restart", d.targetService());
                    cluster.restart(d.targetService());
                    yield "no previous revision to roll back to — performed a rolling restart of " + d.targetService();
                }
                cluster.rollback(d.targetService());
                yield "rolled back " + d.targetService();
            }
            case NONE -> "no action";
        };
    }
}
