package io.sentinelops.agent.remediate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.memory.ScoredIncident;
import io.sentinelops.agent.orchestrator.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * LLM planner backed by Spring AI's {@link ChatClient} (OpenAI-compatible model,
 * so any compatible endpoint works — OpenAI, Azure, a local vLLM, ...). It's given
 * the anomaly, the retrieved precedents (with their fixes), and the cluster state,
 * and returns a structured decision. Any failure (bad response, network, malformed
 * JSON) falls back to the deterministic {@link RuleBasedPlanner} so the loop
 * always decides safely.
 */
public class LlmRemediationPlanner implements RemediationPlanner {

    private static final Logger log = LoggerFactory.getLogger(LlmRemediationPlanner.class);

    private final ChatClient chat;
    private final ObjectMapper mapper;
    private final AgentProperties props;
    private final RuleBasedPlanner fallback;

    public LlmRemediationPlanner(ChatClient chat, ObjectMapper mapper,
                                 AgentProperties props, RuleBasedPlanner fallback) {
        this.chat = chat;
        this.mapper = mapper;
        this.props = props;
        this.fallback = fallback;
    }

    private static final String SYSTEM = """
        You are an SRE remediation planner for a Kubernetes sandbox. Choose EXACTLY one action:
        RESTART (rolling restart), SCALE (add replicas), ROLLBACK (undo last rollout), or NONE.
        Prefer ROLLBACK when the nearest precedent indicates a bad deployment or config change.
        You may act only on a single deployment in the sandbox namespace.
        Respond with ONLY a JSON object:
        {"action":"RESTART|SCALE|ROLLBACK|NONE","target":"<deployment>","replicas":<int>,
         "precedentId":"<id or null>","justification":"<one paragraph citing the precedent>"}
        """;

    @Override
    public RemediationDecision plan(Anomaly anomaly, List<ScoredIncident> precedents, ClusterState clusterState) {
        try {
            String content = call(buildUserPrompt(anomaly, precedents, clusterState));
            return parse(content, anomaly, precedents);
        } catch (Exception e) {
            log.warn("LLM planner failed ({}), falling back to rule-based planner", e.toString());
            return fallback.plan(anomaly, precedents, clusterState);
        }
    }

    private String call(String userPrompt) {
        String content = chat.prompt()
                .system(SYSTEM)
                .user(userPrompt)
                .call()
                .content();
        if (content == null || content.isBlank()) throw new IllegalStateException("empty chat response");
        return content;
    }

    private String buildUserPrompt(Anomaly a, List<ScoredIncident> precedents, ClusterState state) {
        String precedentText = (precedents == null ? List.<ScoredIncident>of() : precedents).stream()
                .limit(5)
                .map(s -> "- id=%s score=%.2f category=%s title=%s | fix=%s".formatted(
                        s.incident().id(), s.score(), s.incident().errorPatternCategory(),
                        s.incident().title(), s.incident().fix()))
                .collect(Collectors.joining("\n"));
        String deployments = state == null ? "(unknown)" : state.deployments().stream()
                .map(d -> "%s(ready=%d/%d)".formatted(d.name(), d.readyReplicas(), d.desiredReplicas()))
                .collect(Collectors.joining(", "));
        return """
            Anomaly: service=%s type=%s detail=%s
            Sandbox deployments: %s
            Retrieved precedents (most similar first):
            %s
            Choose the single best remediation.
            """.formatted(a.service(), a.type(), a.summary(), deployments, precedentText);
    }

    private RemediationDecision parse(String content, Anomaly anomaly, List<ScoredIncident> precedents) throws Exception {
        String json = stripFences(content);
        JsonNode n = mapper.readTree(json);
        RemediationAction action = RemediationAction.valueOf(
                n.path("action").asText("NONE").trim().toUpperCase(Locale.ROOT));
        String target = n.path("target").asText(anomaly.service());
        int replicas = n.path("replicas").asInt(0);
        String justification = n.path("justification").asText("(no justification)");
        String precedentId = n.path("precedentId").isNull() ? null : n.path("precedentId").asText(null);

        ScoredIncident cited = findPrecedent(precedents, precedentId);
        return new RemediationDecision(action, target, props.namespace(), replicas,
                "[llm] " + justification,
                cited == null ? null : cited.incident().id(),
                cited == null ? null : cited.incident().title(),
                cited == null ? null : cited.incident().sourceUrl(),
                name());
    }

    private static ScoredIncident findPrecedent(List<ScoredIncident> precedents, String id) {
        if (precedents == null || precedents.isEmpty()) return null;
        if (id == null) return precedents.get(0);
        return precedents.stream().filter(s -> id.equals(s.incident().id())).findFirst().orElse(precedents.get(0));
    }

    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("^```[a-zA-Z]*\\n", "").replaceAll("```\\s*$", "").trim();
        }
        return t;
    }

    @Override
    public String name() {
        return "llm";
    }
}
