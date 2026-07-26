package io.sentinelops.agent.remediate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.memory.Incident;
import io.sentinelops.agent.memory.ScoredIncident;
import io.sentinelops.agent.orchestrator.AgentProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link LlmRemediationPlanner} against a real Spring AI
 * {@link ChatClient} (OpenAiChatModel/OpenAiApi) talking to a local
 * {@link MockWebServer} standing in for an OpenAI-compatible endpoint — the
 * exact wiring {@link PlannerConfig} builds in production, minus a real key.
 */
class LlmRemediationPlannerTest {

    private MockWebServer server;
    private RuleBasedPlanner fallback;
    private final AgentProperties props =
            new AgentProperties("sentinelops-demo", "sentinelops-demo", false, false, 20, 0.10, 1.0, 0, 4, "llm");

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        fallback = new RuleBasedPlanner(props);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private LlmRemediationPlanner plannerPointedAtMock() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(server.url("/").toString())
                .apiKey("test-key")
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("test-model").temperature(0.0).build())
                .build();
        ChatClient chatClient = ChatClient.builder(model).build();
        return new LlmRemediationPlanner(chatClient, new ObjectMapper(), props, fallback);
    }

    private static ScoredIncident precedent(String id, String category) {
        Incident inc = new Incident(id, "postmortem", "Title " + id, null,
                List.of("svc"), List.of("proxy"), List.of("symptom"), "resource-exhaustion",
                List.of("sig"), category, "root cause", "the fix that worked", "https://example.com/" + id);
        return new ScoredIncident(inc, 0.9, 0.6, 1, 1, 0.5);
    }

    private static String chatCompletionBody(String assistantContent) {
        String escaped = assistantContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
            {"id":"chatcmpl-1","object":"chat.completion","model":"test-model",
             "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """.formatted(escaped);
    }

    @Test
    void parsesADecisionFromARealSpringAiChatClientCall() throws InterruptedException {
        String llmJson = """
            {"action":"SCALE","target":"payment-service","replicas":2,
             "precedentId":"cloudflare-2019-07-02-waf-regex","justification":"CPU precedent says scale up"}""";
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionBody(llmJson)));

        LlmRemediationPlanner planner = plannerPointedAtMock();
        Anomaly anomaly = new Anomaly("payment-service", Anomaly.Type.RESOURCE_EXHAUSTION, 0.9, 0.5, Map.of(), "cpu high");
        List<ScoredIncident> precedents = List.of(precedent("cloudflare-2019-07-02-waf-regex", "cpu-saturation"));

        RemediationDecision decision = planner.plan(anomaly, precedents, null);

        assertThat(decision.action()).isEqualTo(RemediationAction.SCALE);
        assertThat(decision.targetService()).isEqualTo("payment-service");
        assertThat(decision.replicas()).isEqualTo(2);
        assertThat(decision.precedentId()).isEqualTo("cloudflare-2019-07-02-waf-regex");
        assertThat(decision.justification()).contains("CPU precedent says scale up");
        assertThat(decision.planner()).isEqualTo("llm");

        // Confirm the ChatClient actually sent a real chat-completion request.
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).contains("chat/completions");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(req.getBody().readUtf8()).contains("payment-service");
    }

    @Test
    void fallsBackToRuleBasedPlannerWhenTheModelReturnsUnparseableJson() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionBody("not valid json at all")));

        LlmRemediationPlanner planner = plannerPointedAtMock();
        Anomaly anomaly = new Anomaly("inventory-service", Anomaly.Type.CRASH, 0, 1, Map.of(), "0/1 ready");

        RemediationDecision decision = planner.plan(anomaly, List.of(), null);

        // RuleBasedPlanner's rule for CRASH with no precedent -> RESTART.
        assertThat(decision.action()).isEqualTo(RemediationAction.RESTART);
        assertThat(decision.planner()).isEqualTo("rule");
    }

    @Test
    void fallsBackToRuleBasedPlannerWhenTheServerIsUnreachable() throws IOException {
        server.shutdown(); // simulate the endpoint being down

        LlmRemediationPlanner planner = plannerPointedAtMock();
        Anomaly anomaly = new Anomaly("order-service", Anomaly.Type.LATENCY, 3, 1, Map.of(), "p95 3s");

        RemediationDecision decision = planner.plan(anomaly, List.of(), null);

        assertThat(decision.planner()).isEqualTo("rule");
        assertThat(decision.action()).isEqualTo(RemediationAction.RESTART);
    }
}
