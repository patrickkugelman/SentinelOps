package io.sentinelops.agent.embedding;

/** Turns text into a fixed-dimension, L2-normalized vector. */
public interface Embedder {

    float[] embed(String text);

    int dimension();

    /** For logging/diagnostics — which provider is active. */
    String provider();
}
