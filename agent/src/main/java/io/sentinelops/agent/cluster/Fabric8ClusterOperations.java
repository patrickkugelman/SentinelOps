package io.sentinelops.agent.cluster;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.sentinelops.agent.orchestrator.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** fabric8-backed cluster operations, confined to the agent's sandbox namespace. */
@Component
public class Fabric8ClusterOperations implements ClusterOperations {

    private static final Logger log = LoggerFactory.getLogger(Fabric8ClusterOperations.class);
    private static final String NAME_LABEL = "app.kubernetes.io/name";

    private final KubernetesClient client;
    private final String ns;

    public Fabric8ClusterOperations(KubernetesClient client, AgentProperties props) {
        this.client = client;
        this.ns = props.namespace();
    }

    @Override
    public ClusterState getClusterState() {
        List<Deployment> deployments = client.apps().deployments().inNamespace(ns).list().getItems();
        List<ClusterState.DeploymentState> states = deployments.stream().map(this::toDeploymentState).toList();
        return new ClusterState(ns, states);
    }

    private ClusterState.DeploymentState toDeploymentState(Deployment d) {
        String name = d.getMetadata().getName();
        int desired = d.getSpec() != null && d.getSpec().getReplicas() != null ? d.getSpec().getReplicas() : 0;
        int ready = d.getStatus() != null && d.getStatus().getReadyReplicas() != null
                ? d.getStatus().getReadyReplicas() : 0;
        List<ClusterState.PodState> pods = client.pods().inNamespace(ns)
                .withLabel(NAME_LABEL, name).list().getItems().stream()
                .map(Fabric8ClusterOperations::toPodState).toList();
        return new ClusterState.DeploymentState(name, desired, ready, pods);
    }

    private static ClusterState.PodState toPodState(Pod p) {
        String phase = p.getStatus() != null ? p.getStatus().getPhase() : "Unknown";
        boolean ready = false;
        int restarts = 0;
        if (p.getStatus() != null && p.getStatus().getContainerStatuses() != null) {
            var cs = p.getStatus().getContainerStatuses();
            ready = !cs.isEmpty() && cs.stream().allMatch(c -> Boolean.TRUE.equals(c.getReady()));
            restarts = cs.stream().mapToInt(c -> c.getRestartCount() == null ? 0 : c.getRestartCount()).sum();
        }
        return new ClusterState.PodState(p.getMetadata().getName(), phase, ready, restarts);
    }

    @Override
    public String getPodLogs(String service, int lines) {
        List<Pod> pods = client.pods().inNamespace(ns).withLabel(NAME_LABEL, service).list().getItems();
        if (pods.isEmpty()) return "(no pods found for service " + service + ")";
        String pod = pods.get(0).getMetadata().getName();
        return client.pods().inNamespace(ns).withName(pod).tailingLines(lines).getLog();
    }

    @Override
    public void restart(String deployment) {
        log.info("cluster op: rolling restart {}/{}", ns, deployment);
        client.apps().deployments().inNamespace(ns).withName(deployment).rolling().restart();
    }

    @Override
    public void scale(String deployment, int replicas) {
        log.info("cluster op: scale {}/{} -> {} replicas", ns, deployment, replicas);
        client.apps().deployments().inNamespace(ns).withName(deployment).scale(replicas);
    }

    @Override
    public void rollback(String deployment) {
        log.info("cluster op: rollback {}/{} to previous revision", ns, deployment);
        client.apps().deployments().inNamespace(ns).withName(deployment).rolling().undo();
    }

    @Override
    public boolean hasPreviousRevision(String deployment) {
        Deployment d = client.apps().deployments().inNamespace(ns).withName(deployment).get();
        if (d == null || d.getMetadata() == null || d.getMetadata().getAnnotations() == null) return false;
        String revision = d.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision");
        try {
            return revision != null && Integer.parseInt(revision) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public int currentReplicas(String deployment) {
        Deployment d = client.apps().deployments().inNamespace(ns).withName(deployment).get();
        if (d == null || d.getSpec() == null || d.getSpec().getReplicas() == null) return 1;
        return d.getSpec().getReplicas();
    }
}
