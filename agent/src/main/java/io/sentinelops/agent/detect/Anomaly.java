package io.sentinelops.agent.detect;

import java.util.Map;

/** A detected anomaly on a demo service. */
public record Anomaly(
        String service,
        Type type,
        double observedValue,
        double threshold,
        Map<String, Double> metrics,
        String summary) {

    public enum Type {
        // ordered by triage severity (highest first)
        CRASH,
        ERROR_RATE,
        RESOURCE_EXHAUSTION,
        LATENCY
    }
}
