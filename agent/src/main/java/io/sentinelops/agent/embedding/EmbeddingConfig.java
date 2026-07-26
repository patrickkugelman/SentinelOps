package io.sentinelops.agent.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    Embedder embedder(EmbeddingProperties props) {
        if ("openai".equalsIgnoreCase(props.provider())) {
            String key = props.openai().apiKey();
            if (key == null || key.isBlank()) {
                log.warn("sentinelops.embedding.provider=openai but no API key set — "
                        + "falling back to the local embedder.");
                return new LocalHashingEmbedder(props.dimension());
            }
            RestClient http = RestClient.builder()
                    .baseUrl(props.openai().baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .build();
            log.info("Using OpenAI-compatible embeddings: model={} dim={}",
                    props.openai().model(), props.dimension());
            return new OpenAiEmbedder(http, props.openai().model(), props.dimension());
        }
        log.info("Using local hashing embedder: dim={}", props.dimension());
        return new LocalHashingEmbedder(props.dimension());
    }
}
