package io.sentinelops.agent.memory;

/**
 * A retrieved incident with its hybrid score broken out by component, so the
 * dashboard/agent can explain WHY a precedent was chosen.
 */
public record ScoredIncident(
        Incident incident,
        double score,
        double vectorSimilarity,
        double symptomMatch,
        double errorCategoryMatch,
        double serviceOverlap) {
}
