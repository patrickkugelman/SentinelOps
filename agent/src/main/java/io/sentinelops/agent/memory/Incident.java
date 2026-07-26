package io.sentinelops.agent.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * One incident in the knowledge base — a curated public postmortem or a
 * SentinelOps-resolved incident. Field names map to the dataset JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Incident(
        String id,
        String source,
        String title,
        @JsonProperty("date") LocalDate occurredOn,
        @JsonProperty("affected_services") List<String> affectedServices,
        @JsonProperty("service_types") List<String> serviceTypes,
        List<String> symptoms,
        @JsonProperty("symptom_type") String symptomType,
        @JsonProperty("error_signatures") List<String> errorSignatures,
        @JsonProperty("error_pattern_category") String errorPatternCategory,
        @JsonProperty("root_cause") String rootCause,
        String fix,
        @JsonProperty("source_url") String sourceUrl) {

    public Incident {
        if (source == null || source.isBlank()) source = "postmortem";
        affectedServices = affectedServices == null ? List.of() : List.copyOf(affectedServices);
        serviceTypes = serviceTypes == null ? List.of() : List.copyOf(serviceTypes);
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        errorSignatures = errorSignatures == null ? List.of() : List.copyOf(errorSignatures);
    }

    /** Text embedded for the vector side of retrieval. */
    public String embeddingText() {
        return String.join(" | ",
                title,
                String.join(" ", serviceTypes),
                symptomType == null ? "" : symptomType,
                errorPatternCategory == null ? "" : errorPatternCategory,
                String.join(" ", symptoms),
                String.join(" ", errorSignatures),
                rootCause == null ? "" : rootCause,
                fix == null ? "" : fix);
    }
}
