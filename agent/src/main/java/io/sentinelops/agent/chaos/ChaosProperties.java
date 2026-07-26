package io.sentinelops.agent.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaos configuration. {@code allowedNamespace} is a guardrail: the agent will
 * refuse to create or delete chaos anywhere else.
 */
@ConfigurationProperties(prefix = "chaos")
public record ChaosProperties(String namespace, String allowedNamespace) {

    public ChaosProperties {
        if (namespace == null || namespace.isBlank()) namespace = "sentinelops-demo";
        if (allowedNamespace == null || allowedNamespace.isBlank()) allowedNamespace = namespace;
    }
}
