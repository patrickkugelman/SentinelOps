package io.sentinelops.agent.memory;

import java.util.List;

/**
 * The structured "kind of incident" descriptor used for retrieval. Combined with
 * a vector over {@link #description()} for hybrid ranking.
 *
 * @param serviceTypes          affected service categories (proxy, database, ...)
 * @param symptomType           latency | error-rate | crash | resource-exhaustion | ...
 * @param errorPatternCategory  e.g. cpu-saturation, network-latency, bgp-misconfiguration
 * @param description           free text (symptoms, signatures) to embed
 */
public record IncidentSignature(
        List<String> serviceTypes,
        String symptomType,
        String errorPatternCategory,
        String description) {

    public IncidentSignature {
        serviceTypes = serviceTypes == null ? List.of() : List.copyOf(serviceTypes);
    }

    /** Text used for the vector side of retrieval. */
    public String embeddingText() {
        StringBuilder sb = new StringBuilder();
        if (symptomType != null) sb.append(symptomType).append(' ');
        if (errorPatternCategory != null) sb.append(errorPatternCategory).append(' ');
        sb.append(String.join(" ", serviceTypes)).append(' ');
        if (description != null) sb.append(description);
        return sb.toString().trim();
    }
}
