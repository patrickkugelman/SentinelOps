package io.sentinelops.agent.orchestrator;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Mutable runtime switches (so dry-run can be toggled live during a demo). */
@Component
public class AgentRuntime {

    private final AtomicBoolean dryRun;

    public AgentRuntime(AgentProperties props) {
        this.dryRun = new AtomicBoolean(props.dryRun());
    }

    public boolean isDryRun() {
        return dryRun.get();
    }

    public void setDryRun(boolean value) {
        dryRun.set(value);
    }
}
