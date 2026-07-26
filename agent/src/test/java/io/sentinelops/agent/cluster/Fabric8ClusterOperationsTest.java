package io.sentinelops.agent.cluster;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.sentinelops.agent.orchestrator.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Validates the fabric8 read-mapping against a mock Kubernetes API server. */
@EnableKubernetesMockClient(crud = true)
class Fabric8ClusterOperationsTest {

    static KubernetesClient client; // injected by the extension

    private static final String NS = "sentinelops-demo";
    private final AgentProperties props =
            new AgentProperties(NS, NS, false, false, 20, 0.10, 1.0, 0, 4, "rule");

    @Test
    void getClusterStateMapsDeploymentsAndPods() {
        Deployment dep = new DeploymentBuilder()
                .withNewMetadata().withName("inventory-service").withNamespace(NS).endMetadata()
                .withNewSpec().withReplicas(2).endSpec()
                .withNewStatus().withReadyReplicas(1).endStatus()
                .build();
        client.apps().deployments().inNamespace(NS).resource(dep).create();

        Pod pod = new PodBuilder()
                .withNewMetadata().withName("inventory-service-abc").withNamespace(NS)
                    .addToLabels("app.kubernetes.io/name", "inventory-service").endMetadata()
                .withNewStatus().withPhase("Running")
                    .addNewContainerStatus().withName("app").withReady(true).withRestartCount(3).endContainerStatus()
                .endStatus()
                .build();
        client.pods().inNamespace(NS).resource(pod).create();

        Fabric8ClusterOperations ops = new Fabric8ClusterOperations(client, props);
        ClusterState state = ops.getClusterState();

        assertThat(state.namespace()).isEqualTo(NS);
        assertThat(state.deployments()).hasSize(1);
        ClusterState.DeploymentState d = state.deployments().get(0);
        assertThat(d.name()).isEqualTo("inventory-service");
        assertThat(d.desiredReplicas()).isEqualTo(2);
        assertThat(d.readyReplicas()).isEqualTo(1);
        assertThat(d.degraded()).isTrue();
        assertThat(d.pods()).hasSize(1);
        assertThat(d.pods().get(0).ready()).isTrue();
        assertThat(d.pods().get(0).restartCount()).isEqualTo(3);
    }

    @Test
    void currentReplicasReadsSpec() {
        Deployment dep = new DeploymentBuilder()
                .withNewMetadata().withName("payment-service").withNamespace(NS).endMetadata()
                .withNewSpec().withReplicas(1).endSpec()
                .build();
        client.apps().deployments().inNamespace(NS).resource(dep).create();

        Fabric8ClusterOperations ops = new Fabric8ClusterOperations(client, props);
        assertThat(ops.currentReplicas("payment-service")).isEqualTo(1);
    }
}
