package io.sentinelops.agent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Embedding configuration (prefix: {@code sentinelops.embedding}).
 *
 * Default provider is {@code local} — a deterministic, dependency-free embedder
 * so the whole RAG pipeline runs with zero external calls (great for demos/CI).
 * Set provider=openai with an API key for real semantic embeddings.
 */
@ConfigurationProperties(prefix = "sentinelops.embedding")
public record EmbeddingProperties(
        String provider,
        int dimension,
        @NestedConfigurationProperty OpenAi openai) {

    public EmbeddingProperties {
        if (provider == null || provider.isBlank()) provider = "local";
        if (dimension <= 0) dimension = 1536;
        if (openai == null) openai = new OpenAi(null, null, null);
    }

    public record OpenAi(String baseUrl, String apiKey, String model) {
        public OpenAi {
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.openai.com";
            if (model == null || model.isBlank()) model = "text-embedding-3-small";
        }
    }
}
