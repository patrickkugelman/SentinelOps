package io.sentinelops.agent.remediate;

/** What the agent can do to a deployment in the sandbox namespace. */
public enum RemediationAction {
    RESTART,   // rolling restart (recreate pods)
    SCALE,     // increase replicas to add capacity
    ROLLBACK,  // undo the last rollout
    NONE       // no safe action / nothing to do
}
