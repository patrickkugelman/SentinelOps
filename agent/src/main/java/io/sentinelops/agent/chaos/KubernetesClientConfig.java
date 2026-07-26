package io.sentinelops.agent.chaos;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesClientConfig {

    /**
     * fabric8 client. Building is offline (no API call), so the agent still
     * starts without a cluster — chaos/remediation endpoints simply fail at call
     * time. Config resolves from the kube context (KUBECONFIG / ~/.kube/config)
     * on the host, or the service account in-cluster. Spring closes it on shutdown.
     */
    @Bean(destroyMethod = "close")
    KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
