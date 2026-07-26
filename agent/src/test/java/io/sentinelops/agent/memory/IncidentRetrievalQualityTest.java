package io.sentinelops.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.sentinelops.agent.embedding.Embedder;
import io.sentinelops.agent.embedding.LocalHashingEmbedder;
import io.sentinelops.agent.memory.RetrievalProperties.Weights;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retrieval-quality harness. Runs the REAL curated dataset through the REAL
 * local embedder and the hybrid ranker (only the DB recall stage is replaced by
 * an in-memory brute-force cosine, so no Postgres is needed). Proves retrieval
 * reflects "the same KIND of incident": a network/latency signature surfaces
 * network incidents, not crashes — and a CPU-exhaustion signature surfaces
 * resource-exhaustion incidents.
 */
class IncidentRetrievalQualityTest {

    private static List<Incident> dataset;
    private static Embedder embedder;
    private static float[][] embeddings;
    private static HybridRanker ranker;

    @BeforeAll
    static void loadCorpus() {
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        dataset = new IncidentDatasetLoader(mapper).load();
        embedder = new LocalHashingEmbedder(256);
        embeddings = new float[dataset.size()][];
        for (int i = 0; i < dataset.size(); i++) {
            embeddings[i] = embedder.embed(dataset.get(i).embeddingText());
        }
        ranker = new HybridRanker(new Weights(0.50, 0.25, 0.15, 0.10));
    }

    private List<ScoredIncident> retrieve(IncidentSignature sig, int k) {
        float[] q = embedder.embed(sig.embeddingText());
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < dataset.size(); i++) {
            pool.add(new Candidate(dataset.get(i), cosine(q, embeddings[i])));
        }
        return ranker.rank(sig, pool).stream().limit(k).toList();
    }

    @Test
    void networkLatencySignatureRetrievesNetworkIncidentsNotCrashes() {
        IncidentSignature sig = new IncidentSignature(
                List.of("network", "proxy"), "latency", "network-latency",
                "requests to a downstream service are slow, high latency and timeouts, network delay");

        List<ScoredIncident> top = retrieve(sig, 5);

        assertThat(top).isNotEmpty();
        assertThat(isNetworkRelated(top.get(0).incident()))
                .as("top hit should be network-related, was %s", top.get(0).incident().id())
                .isTrue();

        long networkInTop5 = top.stream().filter(s -> isNetworkRelated(s.incident())).count();
        assertThat(networkInTop5).as("most of the top-5 should be network-related").isGreaterThanOrEqualTo(3);

        long crashInTop3 = top.stream().limit(3)
                .filter(s -> "crash".equalsIgnoreCase(s.incident().symptomType())).count();
        assertThat(crashInTop3).as("no crash-type incident should be in the top-3").isZero();
    }

    @Test
    void cpuExhaustionSignatureRetrievesResourceExhaustionIncidents() {
        IncidentSignature sig = new IncidentSignature(
                List.of("proxy", "compute"), "resource-exhaustion", "cpu-saturation",
                "CPU pinned at 100 percent, workers unresponsive, high CPU utilization");

        List<ScoredIncident> top = retrieve(sig, 5);

        long resourceInTop5 = top.stream()
                .filter(s -> "resource-exhaustion".equalsIgnoreCase(s.incident().symptomType())).count();
        assertThat(resourceInTop5).as("CPU query should surface resource-exhaustion incidents")
                .isGreaterThanOrEqualTo(3);

        // The Cloudflare regex/CPU incident is the canonical match — expect it high.
        assertThat(top.stream().limit(3).map(s -> s.incident().id()))
                .contains("cloudflare-2019-07-02-waf-regex");
    }

    @Test
    void scoreBreakdownIsPopulatedForExplainability() {
        IncidentSignature sig = new IncidentSignature(
                List.of("database"), "error-rate", "schema-migration", "database migration broke queries");
        ScoredIncident top = retrieve(sig, 1).get(0);
        assertThat(top.score()).isGreaterThan(0);
        assertThat(top.vectorSimilarity()).isBetween(0.0, 1.0);
    }

    private static boolean isNetworkRelated(Incident i) {
        if (i.serviceTypes().stream().anyMatch(s -> s.equalsIgnoreCase("network"))) return true;
        if ("latency".equalsIgnoreCase(i.symptomType())) return true;
        String cat = i.errorPatternCategory() == null ? "" : i.errorPatternCategory().toLowerCase(Locale.ROOT);
        return cat.contains("network") || cat.contains("bgp") || cat.contains("dns")
                || cat.contains("congestion") || cat.contains("saturation") || cat.contains("partition");
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += (double) a[i] * b[i];
        return dot; // vectors are L2-normalized, so dot == cosine
    }
}
