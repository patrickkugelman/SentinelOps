package io.sentinelops.agent.chaos;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.sentinelops.agent.chaos.ChaosDtos.TriggerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies template loading + parametrization without a cluster. Building the
 * fabric8 client is offline; only its serialization is exercised here.
 */
class ChaosServiceRenderTest {

    private KubernetesClient client;
    private ChaosService service;

    @BeforeEach
    void setUp() {
        client = new KubernetesClientBuilder().build();
        service = new ChaosService(client, new ChaosProperties("sentinelops-demo", "sentinelops-demo"));
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void allFourTemplatesLoadWithExpectedKinds() {
        for (ExperimentDef def : ExperimentDef.ALL) {
            GenericKubernetesResource cr = service.render(def, null);
            assertThat(cr.getKind()).isEqualTo(def.kind());
            assertThat(cr.getApiVersion()).isEqualTo("chaos-mesh.org/v1alpha1");
            assertThat(cr.getMetadata().getNamespace()).isEqualTo("sentinelops-demo");
            assertThat(cr.getMetadata().getName()).startsWith(def.id() + "-");
            assertThat(cr.getMetadata().getLabels())
                    .containsEntry("app.kubernetes.io/managed-by", "sentinelops-agent")
                    .containsEntry("sentinelops.io/experiment", def.id());
        }
    }

    @Test
    void targetOverrideRewritesTheSelector() {
        GenericKubernetesResource cr = service.render(
                ExperimentDef.byId("network-delay"),
                new TriggerRequest("payment-service", null));

        Map<String, Object> spec = cast(cr.getAdditionalProperties().get("spec"));
        Map<String, Object> selector = cast(spec.get("selector"));
        Map<String, Object> labels = cast(selector.get("labelSelectors"));
        assertThat(labels).containsEntry("app.kubernetes.io/name", "payment-service");
        assertThat(selector.get("namespaces")).isEqualTo(List.of("sentinelops-demo"));
        assertThat(cr.getMetadata().getLabels()).containsEntry("sentinelops.io/target", "payment-service");
    }

    @Test
    void durationOverrideAppliesOnlyWhenSupported() {
        Map<String, Object> delaySpec = cast(service
                .render(ExperimentDef.byId("network-delay"), new TriggerRequest(null, 120))
                .getAdditionalProperties().get("spec"));
        assertThat(delaySpec.get("duration")).isEqualTo("120s");

        // pod-kill does not support duration -> no duration key is injected.
        Map<String, Object> podSpec = cast(service
                .render(ExperimentDef.byId("pod-kill"), new TriggerRequest(null, 120))
                .getAdditionalProperties().get("spec"));
        assertThat(podSpec).doesNotContainKey("duration");
    }

    @Test
    void refusesNamespaceOutsideTheGuardrail() {
        ChaosService restricted = new ChaosService(client,
                new ChaosProperties("kube-system", "sentinelops-demo"));
        assertThatThrownBy(() -> restricted.render(ExperimentDef.byId("pod-kill"), null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("restricted to namespace");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }
}
