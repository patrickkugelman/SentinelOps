package io.sentinelops.agent.memory;

import io.sentinelops.agent.embedding.Embedder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The incident-memory RAG service: ingest the curated dataset, retrieve the most
 * similar precedents for an anomaly signature (hybrid vector + structured), and
 * write resolved incidents back so the memory grows over time.
 */
@Service
public class IncidentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(IncidentMemoryService.class);

    private final IncidentRepository repository;
    private final IncidentDatasetLoader loader;
    private final Embedder embedder;
    private final HybridRanker ranker;
    private final RetrievalProperties retrieval;

    public IncidentMemoryService(IncidentRepository repository, IncidentDatasetLoader loader,
                                 Embedder embedder, RetrievalProperties retrieval) {
        this.repository = repository;
        this.loader = loader;
        this.embedder = embedder;
        this.retrieval = retrieval;
        this.ranker = new HybridRanker(retrieval.weights());
    }

    /** Embed and upsert the whole curated dataset. Returns how many were stored. */
    public int ingestDataset() {
        List<Incident> incidents = loader.load();
        for (Incident inc : incidents) {
            repository.save(inc, embedder.embed(inc.embeddingText()));
        }
        log.info("Ingested {} postmortems into incident memory (embedder={})",
                incidents.size(), embedder.provider());
        return incidents.size();
    }

    /** Hybrid retrieval: vector recall then structured re-rank. */
    public List<ScoredIncident> retrieve(IncidentSignature signature, int topK) {
        float[] queryVec = embedder.embed(signature.embeddingText());
        List<Candidate> pool = repository.recallByVector(queryVec, retrieval.recallPoolSize());
        List<ScoredIncident> ranked = ranker.rank(signature, pool);
        int k = topK > 0 ? topK : retrieval.topK();
        return ranked.stream().limit(k).toList();
    }

    public List<ScoredIncident> retrieve(IncidentSignature signature) {
        return retrieve(signature, retrieval.topK());
    }

    /**
     * Write-back: record a SentinelOps-resolved incident into the same store,
     * always tagged source=sentinelops so it's distinguishable from curated
     * postmortems (and so the memory visibly grows as the agent resolves things).
     */
    public void record(Incident resolved) {
        Incident tagged = new Incident(resolved.id(), "sentinelops", resolved.title(), resolved.occurredOn(),
                resolved.affectedServices(), resolved.serviceTypes(), resolved.symptoms(),
                resolved.symptomType(), resolved.errorSignatures(), resolved.errorPatternCategory(),
                resolved.rootCause(), resolved.fix(), resolved.sourceUrl());
        repository.save(tagged, embedder.embed(tagged.embeddingText()));
        log.info("Recorded resolved incident {} into memory", tagged.id());
    }

    public long count() {
        return repository.count();
    }
}
