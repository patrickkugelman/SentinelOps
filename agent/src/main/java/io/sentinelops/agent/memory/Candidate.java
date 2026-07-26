package io.sentinelops.agent.memory;

/** An incident recalled by the vector stage, with its cosine similarity. */
public record Candidate(Incident incident, double vectorSimilarity) {
}
