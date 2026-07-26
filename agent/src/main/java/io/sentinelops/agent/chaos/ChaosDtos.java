package io.sentinelops.agent.chaos;

import java.time.Instant;

/** Request/response shapes for the chaos API. */
public final class ChaosDtos {
    private ChaosDtos() { }

    /** Optional overrides when triggering an experiment. */
    public record TriggerRequest(String target, Integer durationSeconds) {
        public TriggerRequest {
            if (durationSeconds != null && durationSeconds <= 0) {
                throw new IllegalArgumentException("durationSeconds must be positive");
            }
        }
    }

    /** A live (or just-created) chaos experiment. */
    public record ChaosHandle(
            String name,
            String experimentId,
            String kind,
            String target,
            String namespace,
            Instant startedAt) { }
}
