package io.sentinelops.agent.remediate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OpenAI-compatible chat config for the LLM planner (prefix: {@code sentinelops.agent.llm}). */
@ConfigurationProperties(prefix = "sentinelops.agent.llm")
public record ChatProperties(String baseUrl, String apiKey, String model) {

    public ChatProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.openai.com";
        if (model == null || model.isBlank()) model = "gpt-4o-mini";
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
