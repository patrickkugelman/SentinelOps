# SentinelOps — Architecture

> Skeleton. Fleshed out with a rendered diagram in Phase 8. This captures the
> intended component boundaries so every phase builds toward the same picture.

## One-liner
An SRE agent with **institutional memory**: on a Kubernetes anomaly it retrieves
the most similar *real* past incident (from public postmortems + its own
history), reasons over that precedent's root cause and fix, then remediates —
instead of reacting from scratch.

## Components

```mermaid
flowchart LR
  subgraph host["Host / WSL2"]
    dash["Dashboard<br/>Vue 3 + Vite"]
    agent["Agent<br/>Spring Boot 3 · Java 21"]
    pg[("Postgres + pgvector<br/>incident memory")]
  end
  subgraph kind["kind cluster (Docker)"]
    prom["Prometheus"]
    chaos["Chaos Mesh"]
    subgraph ns["namespace: sentinelops-demo"]
      order["order-service"]
      inv["inventory-service"]
      pay["payment-service"]
      ddb[("demo Postgres")]
    end
  end

  dash <-->|"SSE trace + REST"| agent
  agent -->|"queryPrometheus"| prom
  agent -->|"getClusterState / remediate"| ns
  agent <-->|"retrieve / record"| pg
  agent -->|"inject / stop"| chaos
  prom -.->|"scrape /actuator/prometheus"| ns
  chaos -.->|"faults"| ns
  order --> inv
  order --> pay
  order --> ddb
```

## Incident-response loop

```mermaid
sequenceDiagram
  autonumber
  participant U as Dashboard
  participant A as Agent
  participant P as Prometheus
  participant M as Incident Memory<br/>(pgvector)
  participant K as kind cluster

  U->>A: inject chaos, then respond
  A->>P: queryPrometheus (error-rate, p95)
  A->>K: getClusterState
  A->>A: build incident signature
  A->>M: retrieveSimilarIncidents(signature)
  M-->>A: top-k precedents (root cause, fix, source_url)
  A->>A: reason — which precedent's fix applies, and why
  A->>K: remediate: restart / scale / rollback (guardrailed)
  A->>P: verify recovery (before/after)
  A->>M: recordIncidentOutcome (memory grows)
  A-->>U: stream reasoning trace (SSE)
```

## Data flow: one incident
1. **Detect** — agent polls `queryPrometheus` for latency/error-rate/resource
   anomalies in `sentinelops-demo`.
2. **Signature** — build a structured signature: `service_types`,
   `error_pattern_category`, `symptom_type`.
3. **Retrieve** — `retrieveSimilarIncidents(signature)` = structured match +
   pgvector cosine, top-k.
4. **Reason** — LLM decides which precedent's fix applies and why.
5. **Remediate** — `remediate(action, target)` (restart/scale/rollback), inside
   the sandbox namespace only, dry-run aware.
6. **Verify** — watch post-action metrics recover.
7. **Record** — `recordIncidentOutcome(...)` embeds and writes the resolved
   incident back into the same store.

## Safety model
- Namespace allow-list (`sentinelops-demo`); refuses everything else.
- Dry-run mode.
- Every remediation logged with justification **before** execution.

## Trust boundaries
- The LLM sees metrics, logs, cluster state, and retrieved precedents — all
  treated as **data**. Tool calls are the only way it affects the cluster, and
  every tool enforces the namespace guardrail server-side (not by prompt).
