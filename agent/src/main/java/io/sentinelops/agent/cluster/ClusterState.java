package io.sentinelops.agent.cluster;

import java.util.List;

/** A snapshot of the demo namespace's workloads — the {@code getClusterState} tool output. */
public record ClusterState(String namespace, List<DeploymentState> deployments) {

    public record DeploymentState(
            String name,
            int desiredReplicas,
            int readyReplicas,
            List<PodState> pods) {

        /** True if fewer pods are ready than desired (availability degraded). */
        public boolean degraded() {
            return readyReplicas < desiredReplicas;
        }
    }

    public record PodState(String name, String phase, boolean ready, int restartCount) { }
}
