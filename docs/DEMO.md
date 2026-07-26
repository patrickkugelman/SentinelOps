# Demo scenarios & interview script

Three (well, four) distinct failure modes, each detected, matched to a real
public postmortem, remediated, and recorded — visibly, on the dashboard.

## Setup (once)

```bash
./scripts/setup.sh                 # infra + kind + observability + chaos + workload
./scripts/install-observability.sh # if not already: Prometheus
./scripts/install-chaos-mesh.sh    # if not already: Chaos Mesh
cd agent && mvn spring-boot:run &   # the agent (auto-ingests 38 postmortems)
cd dashboard && npm run dev         # http://localhost:5173
```

Open **http://localhost:5173** and keep it visible — every scenario streams there.

Each scenario can be run hands-free:

```bash
./scripts/demo.sh <scenario>
```

---

## Scenario 1 — Pod kill (crash)

```bash
./scripts/demo.sh pod-kill
```

- **Chaos:** kills an `inventory-service` pod.
- **Detect:** cluster state shows `inventory-service` degraded (ready < desired).
- **Signature:** `symptom=crash, category=process-crash, types=[compute, api]`.
- **Retrieve:** crash/power-loss precedents (e.g. GitHub's 2016 datacenter power
  incident) — *not* network or latency ones.
- **Decide:** `RESTART inventory-service` — rolling restart to recover a clean set.
- **Watch:** the pod chip flips red→green in the Cluster panel; the trace shows
  the retrieved precedent (linked) and the justification.

## Scenario 2 — Network delay (cascading latency)

```bash
./scripts/demo.sh network-delay
```

- **Chaos:** 4s latency injected into `inventory-service`.
- **Detect:** `order-service` p95 latency and error rate climb (its 2s read-timeout
  trips as the dependency slows) — a **cascading failure**, not a local one.
- **Signature:** `symptom=latency, category=network-latency, types=[network, api]`.
- **Retrieve:** network/latency postmortems (Cloudflare backbone congestion, Slack
  Vitess overload) — the retrieval-quality property in action.
- **Decide:** `RESTART` to shed degraded pods (or `ROLLBACK` if the top precedent
  points at a bad change).
- **Watch:** the error-rate chart spikes then recovers after the action.

## Scenario 3 — CPU stress (resource exhaustion)

```bash
./scripts/demo.sh cpu-stress
```

- **Chaos:** pins CPU on `payment-service`.
- **Detect:** payment latency rises; checkout degrades.
- **Signature:** `symptom=resource-exhaustion, category=cpu-saturation`.
- **Retrieve:** the **Cloudflare 2019 WAF-regex CPU** incident sits at the top —
  the canonical CPU-exhaustion precedent.
- **Decide:** `SCALE payment-service` — add a replica for capacity.
- **Watch:** replica count increases in the Cluster panel; latency settles.

## Scenario 4 (bonus) — Network partition (hard errors)

```bash
./scripts/demo.sh network-partition
```

- **Chaos:** cuts `order-service` off from `inventory-service`.
- **Detect:** checkout fails fast — `order-service` 5xx ratio jumps.
- **Signature:** `symptom=error-rate, category=http-5xx`.
- **Retrieve:** partition/BGP/connectivity postmortems (GitHub's 43-second
  partition, Meta's BGP withdrawal).
- **Decide:** `RESTART` / `ROLLBACK` depending on the nearest precedent.

---

## The 5-minute interview script

**Frame it (30s).** "Most self-healing infra reacts to symptoms — CPU high, scale
up. SentinelOps is different: when it sees an anomaly it first retrieves the most
similar *real* past incident from a knowledge base of public postmortems plus its
own history, and uses that precedent's root cause and proven fix to decide what to
do. It's an SRE agent with institutional memory."

**Show the board (30s).** Point at the dashboard: live cluster health, the incident
memory size (38 curated postmortems), chaos controls, an empty reasoning trace.

**Run scenario 3 — CPU stress (90s).** Click it (or `./scripts/demo.sh cpu-stress`).
Narrate the trace as it streams:
1. "It detected resource exhaustion on payment-service."
2. "It built a *structured signature* and retrieved precedents — notice the top hit
   is the **Cloudflare 2019 regex CPU outage**, a real postmortem, linked. That's
   hybrid retrieval: vector similarity *plus* a structured incident signature, so it
   matches the *kind* of incident, not just similar words."
3. "It reasoned over that precedent and chose to scale — and every decision is logged
   with a justification *before* it executes."
4. "Metrics recover, and it writes the resolved incident back into the same memory —
   so the system gets better over time."

**Hit the safety angle (45s).** Toggle **dry-run** and re-run: "In dry-run it reasons
and logs but never touches the cluster. It also physically can't act outside the
`sentinelops-demo` namespace — that guardrail is enforced in the tool layer, not the
prompt." (There's a unit test that asserts a foreign-namespace action is refused.)

**Architecture (60s).** Walk the diagram in the README: Vue dashboard ↔ Spring Boot
agent (tools: `queryPrometheus`, `getClusterState`, `getPodLogs`,
`retrieveSimilarIncidents`, `remediate`, `recordIncidentOutcome`) ↔ Prometheus,
pgvector, and a kind cluster running three Spring Boot microservices with Chaos Mesh.

**Close (15s).** "Runs on a laptop with one script. The retrieval is the interesting
part — happy to go deep on the hybrid ranking or the safety model."

### Questions you'll likely get — and the answers
- **"Is the LLM required?"** No. The default planner is deterministic and runs with
  no API key; the LLM is an optional, swappable planner behind an interface (with a
  rule-based fallback). Embeddings likewise default to a local, dependency-free
  embedder.
- **"How is retrieval more than vector search?"** Two stages: pgvector cosine recall,
  then a re-rank that combines similarity with a structured signature (symptom type,
  error-pattern category, service-type overlap). A test asserts a network/latency
  query returns network incidents, not crashes.
- **"How do you keep it from doing something dangerous?"** Namespace allow-list
  (enforced in code), dry-run mode, and justification logged before every action.
- **"What's real vs. mocked?"** The services, cluster, Prometheus, Chaos Mesh, and
  pgvector are all real and local. The postmortems are curated original summaries of
  real public incidents, each linked to its source.
