package io.sentinelops.agent.cluster;

/**
 * Read + remediation operations against the sandbox namespace. These are the
 * agent's cluster tools: {@code getClusterState}, {@code getPodLogs}, and the
 * three {@code remediate} actions. Every mutating call is guardrailed to the
 * allowed namespace by the implementation.
 */
public interface ClusterOperations {

    ClusterState getClusterState();

    /** Tail logs from a pod of the given service (by app label). */
    String getPodLogs(String service, int lines);

    /** Rolling restart of a deployment (pods recreated). */
    void restart(String deployment);

    /** Scale a deployment to an absolute replica count. */
    void scale(String deployment, int replicas);

    /** Roll a deployment back to its previous revision. */
    void rollback(String deployment);

    /**
     * True if the deployment has a prior revision to roll back to. A freshly
     * installed deployment has only revision 1, so a rollback would fail.
     */
    boolean hasPreviousRevision(String deployment);

    /** Current desired replicas for a deployment (for scale decisions). */
    int currentReplicas(String deployment);
}
