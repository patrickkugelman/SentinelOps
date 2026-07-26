package io.sentinelops.agent.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Retrieval tuning (prefix: {@code sentinelops.retrieval}).
 *
 * @param topK           results returned to the caller
 * @param recallPoolSize how many candidates the vector stage pulls before re-rank
 * @param weights        hybrid-score component weights
 */
@ConfigurationProperties(prefix = "sentinelops.retrieval")
public record RetrievalProperties(
        int topK,
        int recallPoolSize,
        @NestedConfigurationProperty Weights weights) {

    public RetrievalProperties {
        if (topK <= 0) topK = 5;
        if (recallPoolSize <= 0) recallPoolSize = 30;
        if (weights == null) weights = new Weights(0, 0, 0, 0);
    }

    public record Weights(double vector, double symptom, double errorCategory, double service) {
        public Weights {
            // Sensible defaults if unset: structured signals collectively outweigh
            // the (possibly weak) vector, which is what makes retrieval reflect
            // "the same KIND of incident".
            if (vector == 0 && symptom == 0 && errorCategory == 0 && service == 0) {
                vector = 0.50;
                symptom = 0.25;
                errorCategory = 0.15;
                service = 0.10;
            }
        }
    }
}
