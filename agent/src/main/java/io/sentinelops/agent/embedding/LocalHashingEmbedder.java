package io.sentinelops.agent.embedding;

import java.util.Locale;

/**
 * Deterministic, dependency-free embedder: a hashed bag-of-words (feature
 * hashing) with L2 normalization. No network, no API key — the pipeline runs
 * anywhere. Semantic quality is lexical only; the hybrid retriever leans on the
 * structured signature for the "same kind of incident" signal, so retrieval
 * quality holds up even with this embedder. Swap to OpenAI for stronger vectors.
 */
public class LocalHashingEmbedder implements Embedder {

    private final int dimension;

    public LocalHashingEmbedder(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimension];
        if (text == null || text.isBlank()) {
            v[0] = 1.0f; // avoid an all-zero vector (undefined cosine)
            return v;
        }
        for (String token : tokenize(text)) {
            int h = token.hashCode();
            int idx = Math.floorMod(h, dimension);
            // Sign from a second hash so features can cancel — reduces collisions.
            float sign = ((h >>> 31) == 0) ? 1.0f : -1.0f;
            v[idx] += sign;
        }
        normalize(v);
        return v;
    }

    private static String[] tokenize(String text) {
        return text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
    }

    private static void normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            v[0] = 1.0f;
            return;
        }
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String provider() {
        return "local";
    }
}
