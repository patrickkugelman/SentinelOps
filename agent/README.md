# Agent orchestration — `agent/`

Spring Boot 3 (Java 21) + Spring AI service. The "SRE agent with institutional
memory." Grown across **Phases 3, 5, and 6**.

## Implemented so far (Phase 3)
- **`prometheus/PrometheusClient`** — the `queryPrometheus` tool: instant query,
  range query, scalar convenience, reachability check. Parses the Prometheus
  HTTP API (vector + matrix). Config via `prometheus.url` (env `PROMETHEUS_URL`).
- **`web/MetricsController`** — HTTP surface so it's demoable standalone:
  `/api/prometheus/query?q=`, `/api/prometheus/query_range`, and ready-made
  `/api/prometheus/demo/{request-rate,error-rate}`.
- Runs on port **8090** (env `AGENT_PORT`). `mvn spring-boot:run` or the Dockerfile.

## Implemented so far (Phase 4 — chaos)
- **`chaos/ChaosService` + `ChaosController`** — `/api/chaos/*` to trigger/stop the
  four experiment templates (fabric8), guardrailed to `sentinelops-demo`.

## Implemented so far (Phase 5 — incident memory)
- **`embedding/`** — `Embedder` with a local hashing default and an
  OpenAI-compatible implementation (`EMBEDDING_PROVIDER`).
- **`memory/`** — pgvector store (Flyway `V1`, HNSW), two-stage hybrid retrieval
  (`IncidentRepository` recall → `HybridRanker` re-rank), dataset auto-ingest,
  and write-back. API: `web/IncidentController` (`/api/incidents/*`).
- Requires the Postgres+pgvector container (incident-memory store).

## Implemented so far (Phase 6 — orchestration)
- **`cluster/`** — `ClusterOperations` (fabric8): `getClusterState`, `getPodLogs`,
  restart / scale / rollback.
- **`detect/`** — `AnomalyDetector` (error-rate, p95 latency, degraded pods) and
  `SignatureFactory` (anomaly → `IncidentSignature`).
- **`remediate/`** — `RemediationPlanner` (precedent-driven `RuleBasedPlanner`
  default + optional `LlmRemediationPlanner` backed by **Spring AI**'s
  `ChatClient`/`OpenAiChatModel` — OpenAI-compatible, built by hand in
  `PlannerConfig` only when `AGENT_PLANNER=llm` + a key are set, so the agent
  never touches Spring AI without one; any failure falls back to the rule
  planner), `RemediationExecutor` (guardrails).
- **`orchestrator/`** — `IncidentResponseOrchestrator` runs the loop and emits an
  `AgentTrace`; `AgentRuntime` holds the live dry-run toggle; optional
  `AutoRemediateScheduler`. API: `web/AgentController` (`/api/agent/*`).

Original tool list / decision loop / guardrails below.

## Design reference

**Tools the agent can call**
- `queryPrometheus(query)` — read metrics
- `getPodLogs(pod, lines)` — tail logs
- `getClusterState()` — pods/services health in the sandbox namespace
- `retrieveSimilarIncidents(signature)` — hybrid (structured + vector) recall
- `remediate(action, target)` — restart / scale / rollback
- `recordIncidentOutcome(...)` — write the resolved incident back to memory

**Decision loop**
detect anomaly → build incident signature → retrieve top-k precedents →
reason over which precedent's fix applies and why → execute remediation →
monitor post-action metrics → record the outcome.

**Guardrails**
- Acts **only** inside `${AGENT_ALLOWED_NAMESPACE}` (`sentinelops-demo`).
- `AGENT_DRY_RUN=true` reasons + logs but does not execute.
- Every remediation is logged with full justification **before** execution.

> Populated in Phases 5–6.
