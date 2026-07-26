# Incident Memory — `incident-memory/`

The RAG knowledge base of real, public postmortems + SentinelOps' own resolved
incidents. **Implemented in Phase 5.**

- **Dataset (canonical):** [`agent/src/main/resources/incidents/postmortems.json`](../agent/src/main/resources/incidents/postmortems.json)
  — currently **38** curated records; extensible toward 100.
- **Schema:** [`postmortems.schema.json`](postmortems.schema.json)
- **Validate:** `./incident-memory/validate.sh` (schema + duplicate-id check)
- **Pipeline (in the agent):** `embedding/` (Embedder: local + OpenAI-compatible),
  `memory/` (repository/pgvector, `HybridRanker`, `IncidentMemoryService`).

The dataset lives under the agent's resources so it's packaged into the agent
jar/image; this directory holds the schema and tooling.

## Dataset
Curated, **original** structured summaries of real public postmortems
(Cloudflare, AWS, GitHub, GitLab, Google, Meta, Fastly, Slack, Datadog, Reddit,
Roblox, …). We do **not** redistribute source text — each record is written in
our own words and links to the authoritative `source_url`.

Record schema (`postmortems.schema.json`):
```json
{
  "id": "cloudflare-2019-07-02-regex",
  "title": "Cloudflare global outage from a bad WAF regex",
  "date": "2019-07-02",
  "affected_services": ["edge-proxy", "waf"],
  "service_types": ["proxy", "security"],
  "symptoms": ["global 502s", "cpu exhaustion"],
  "symptom_type": "resource-exhaustion",
  "error_signatures": ["502 Bad Gateway", "CPU 100%"],
  "error_pattern_category": "cpu-saturation",
  "root_cause": "A regex with catastrophic backtracking was deployed globally...",
  "fix": "Global kill switch for WAF; reverted the rule; added CPU guardrails...",
  "source_url": "https://blog.cloudflare.com/details-of-the-cloudflare-outage-on-july-2-2019/"
}
```

## Retrieval (hybrid, two-stage)
Not pure text similarity:
1. **Vector recall** — pgvector cosine (`embedding <=> query`, HNSW index) pulls a
   candidate pool.
2. **Structured re-rank** (`HybridRanker`) — combines cosine with a structured
   **incident signature**: symptom-type match, error-pattern-category match, and
   service-type Jaccard overlap. Weights are configurable
   (`sentinelops.retrieval.weights`, default vector 0.5 / symptom 0.25 /
   error-category 0.15 / service 0.10).

So a `network-delay` (latency) signature retrieves network/latency postmortems,
not crashes — and a `cpu-saturation` signature retrieves resource-exhaustion
incidents. `IncidentRetrievalQualityTest` asserts exactly this on the real dataset,
and it's verified live against pgvector.

## Retrieval-quality evaluation harness (Python)
`retrieval_eval.py` is a black-box eval tool — it hits the **live** agent API
(not the in-process ranker like the Java test) with a benchmark set of incident
signatures spanning all four symptom types, and reports precision@1, precision@3,
and MRR:

```bash
cd incident-memory
pip install -r requirements.txt
python retrieval_eval.py                                  # against localhost:8090
python retrieval_eval.py --json-report report.json         # write a JSON report
python retrieval_eval.py --min-precision-at-3 0.3           # CI gate: non-zero exit if below
```

Sample run against the live dataset: **precision@1 = 1.00, precision@3 = 0.96, MRR = 1.00**
across 8 benchmark queries (network delay, CPU saturation, pod crash, network
partition, data deletion, BGP misconfiguration, schema migration, expired
certificate) — including confirming the canonical Cloudflare 2019 regex
postmortem surfaces for the CPU-saturation query.

## Write-back
Every incident SentinelOps resolves is embedded and inserted into the **same**
store, tagged `source=sentinelops`, so institutional memory grows over time
(`POST /api/incidents/record`).

## API
- `GET  /api/incidents/count`
- `POST /api/incidents/ingest` — (re)embed + upsert the whole dataset
- `POST /api/incidents/retrieve?topK=5` — body is an `IncidentSignature`
- `POST /api/incidents/record` — write-back a resolved incident

Embeddings default to a **local, dependency-free** embedder so the pipeline runs
with no API key; set `EMBEDDING_PROVIDER=openai` (+ `OPENAI_API_KEY`) for real
semantic vectors.
