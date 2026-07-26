package io.sentinelops.agent.remediate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelops.agent.orchestrator.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the active planner. Default is rule-based; set
 * {@code sentinelops.agent.planner=llm} (with an API key) to reason with an LLM
 * via Spring AI's {@link ChatClient}. The ChatClient/ChatModel beans are built
 * here by hand (no Spring AI autoconfiguration) so the agent never touches
 * Spring AI at all — and never needs a key — unless this branch runs.
 * The LLM planner always keeps the rule planner as a safety fallback.
 */
@Configuration
public class PlannerConfig {

    private static final Logger log = LoggerFactory.getLogger(PlannerConfig.class);

    @Bean
    RuleBasedPlanner ruleBasedPlanner(AgentProperties props) {
        return new RuleBasedPlanner(props);
    }

    @Bean
    @Primary
    RemediationPlanner remediationPlanner(AgentProperties agentProps, ChatProperties chatProps,
                                          RuleBasedPlanner ruleBased, ObjectMapper mapper) {
        boolean wantsLlm = "llm".equalsIgnoreCase(agentProps.planner());
        if (wantsLlm && chatProps.hasKey()) {
            ChatClient chatClient = buildChatClient(chatProps);
            log.info("Remediation planner: LLM via Spring AI ({} @ {}), rule-based fallback",
                    chatProps.model(), chatProps.baseUrl());
            return new LlmRemediationPlanner(chatClient, mapper, agentProps, ruleBased);
        }
        if (wantsLlm) {
            log.warn("planner=llm but no API key set — using rule-based planner");
        } else {
            log.info("Remediation planner: rule-based");
        }
        return ruleBased;
    }

    private static ChatClient buildChatClient(ChatProperties chatProps) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(chatProps.baseUrl())
                .apiKey(chatProps.apiKey())
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatProps.model())
                        .temperature(0.0)
                        .build())
                .build();
        return ChatClient.builder(model).build();
    }
}
