package io.sentinelops.agent.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent behaviour + safety (prefix: {@code sentinelops.agent}).
 *
 * @param namespace          the sandbox namespace the agent operates in
 * @param allowedNamespace   guardrail: refuse any remediation outside this ns
 * @param dryRun             reason + log but do NOT execute remediations
 * @param autoRemediate      run the response loop on a schedule (default off)
 * @param pollSeconds        schedule interval when autoRemediate is on
 * @param errorRateThreshold 5xx ratio above which a service is anomalous (0..1)
 * @param latencyP95Seconds  p95 latency above which a service is anomalous
 * @param verifySeconds      how long to watch metrics after acting (0 = skip)
 * @param maxReplicas        cap when scaling up
 * @param planner            "rule" | "llm"
 */
@ConfigurationProperties(prefix = "sentinelops.agent")
public record AgentProperties(
        String namespace,
        String allowedNamespace,
        boolean dryRun,
        boolean autoRemediate,
        int pollSeconds,
        double errorRateThreshold,
        double latencyP95Seconds,
        int verifySeconds,
        int maxReplicas,
        String planner) {

    public AgentProperties {
        if (namespace == null || namespace.isBlank()) namespace = "sentinelops-demo";
        if (allowedNamespace == null || allowedNamespace.isBlank()) allowedNamespace = namespace;
        if (pollSeconds <= 0) pollSeconds = 20;
        if (errorRateThreshold <= 0) errorRateThreshold = 0.10;
        if (latencyP95Seconds <= 0) latencyP95Seconds = 1.0;
        if (verifySeconds < 0) verifySeconds = 0;
        if (maxReplicas <= 0) maxReplicas = 4;
        if (planner == null || planner.isBlank()) planner = "rule";
    }
}
