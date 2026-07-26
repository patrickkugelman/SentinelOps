package io.sentinelops.agent.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashingEmbedderTest {

    private final Embedder embedder = new LocalHashingEmbedder(128);

    @Test
    void isDeterministic() {
        assertThat(embedder.embed("network latency timeout"))
                .isEqualTo(embedder.embed("network latency timeout"));
    }

    @Test
    void isL2Normalized() {
        float[] v = embedder.embed("cpu saturation worker unresponsive");
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void differentTextProducesDifferentVectors() {
        assertThat(embedder.embed("network partition bgp"))
                .isNotEqualTo(embedder.embed("cpu memory exhaustion"));
    }

    @Test
    void blankTextIsHandled() {
        float[] v = embedder.embed("");
        assertThat(v).hasSize(128);
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        assertThat(Math.sqrt(norm)).isGreaterThan(0);
    }

    @Test
    void dimensionIsHonored() {
        assertThat(new LocalHashingEmbedder(1536).embed("x")).hasSize(1536);
    }
}
