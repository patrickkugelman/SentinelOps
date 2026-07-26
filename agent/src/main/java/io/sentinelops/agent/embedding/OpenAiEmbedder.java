package io.sentinelops.agent.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible embeddings via POST {baseUrl}/v1/embeddings. Works with
 * OpenAI, Azure OpenAI, or any compatible endpoint (vLLM, etc.). The returned
 * vector is L2-normalized so cosine and dot product agree with the local embedder.
 */
public class OpenAiEmbedder implements Embedder {

    private final RestClient http;
    private final String model;
    private final int dimension;

    public OpenAiEmbedder(RestClient http, String model, int dimension) {
        this.http = http;
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse resp = http.post()
                .uri("/v1/embeddings")
                .body(Map.of(
                        "model", model,
                        "input", text,
                        "dimensions", dimension))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            throw new IllegalStateException("empty embedding response from provider");
        }
        List<Double> raw = resp.data().get(0).embedding();
        float[] v = new float[raw.size()];
        double sum = 0;
        for (int i = 0; i < raw.size(); i++) {
            v[i] = raw.get(i).floatValue();
            sum += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String provider() {
        return "openai:" + model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<Item> data) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(List<Double> embedding) { }
}
