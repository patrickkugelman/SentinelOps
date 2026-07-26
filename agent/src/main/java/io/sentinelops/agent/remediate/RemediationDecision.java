package io.sentinelops.agent.remediate;

/**
 * The agent's chosen remediation, with the precedent that informed it and a
 * full human-readable justification (logged before any execution).
 *
 * @param action        what to do
 * @param targetService deployment to act on
 * @param namespace     namespace (must be the allowed one)
 * @param replicas      target replica count (SCALE only; else ignored)
 * @param justification why — references the precedent + the anomaly
 * @param precedentId   the retrieved precedent that informed the decision
 * @param precedentTitle
 * @param precedentUrl  source link to the precedent postmortem
 * @param planner       which planner produced this ("rule" | "llm")
 */
public record RemediationDecision(
        RemediationAction action,
        String targetService,
        String namespace,
        int replicas,
        String justification,
        String precedentId,
        String precedentTitle,
        String precedentUrl,
        String planner) {

    public static RemediationDecision none(String namespace, String reason, String planner) {
        return new RemediationDecision(RemediationAction.NONE, null, namespace, 0, reason,
                null, null, null, planner);
    }
}
