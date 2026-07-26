package io.sentinelops.agent.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional autonomous loop: periodically runs a response cycle so the agent
 * self-heals without a manual trigger. Off by default
 * ({@code sentinelops.agent.auto-remediate=false}); the demo drives responses
 * from the dashboard instead.
 */
@Component
public class AutoRemediateScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoRemediateScheduler.class);

    private final IncidentResponseOrchestrator orchestrator;
    private final AgentProperties props;

    public AutoRemediateScheduler(IncidentResponseOrchestrator orchestrator, AgentProperties props) {
        this.orchestrator = orchestrator;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "#{${sentinelops.agent.poll-seconds:20} * 1000}")
    public void tick() {
        if (!props.autoRemediate()) return;
        try {
            AgentTrace trace = orchestrator.respond(null, null, 5);
            if (trace.getAnomaly() != null) {
                log.info("Auto-remediate acted on {}", trace.getAnomaly().summary());
            }
        } catch (Exception e) {
            log.warn("Auto-remediate tick failed: {}", e.toString());
        }
    }
}
