# Runbook — setup, run & verify

All commands run inside **WSL2 (Ubuntu) + Docker Desktop**. See the
[README](../README.md) for prerequisites.

## Prerequisites check

```bash
./scripts/preflight.sh   # docker, kubectl, kind, helm (+ Java/Node hints)
```

## One-shot bring-up

```bash
cp .env.example .env
./scripts/setup.sh                     # Postgres, kind, (stubs call obs/chaos/workload)
./scripts/install-observability.sh     # Prometheus (+ --with-grafana)
./scripts/install-chaos-mesh.sh        # Chaos Mesh
./scripts/deploy-workload.sh           # build images -> kind load -> helm install
cd agent && mvn spring-boot:run &       # agent :8090 (auto-ingests the dataset)
cd dashboard && npm install && npm run dev   # dashboard :5173
```

Teardown: `./scripts/teardown.sh` (add `--wipe` to drop the DB volume).

---

## Phase 1 — infra
```bash
docker compose ps                       # sentinelops-postgres healthy
make psql                               # \dx shows 'vector'
```

## Phase 2 — demo microservices
```bash
./scripts/kind-up.sh && ./scripts/deploy-workload.sh && ./scripts/smoke-demo.sh
curl -s -X POST http://localhost:30080/orders/checkout \
  -H 'Content-Type: application/json' -d '{"sku":"SKU-LAPTOP","quantity":1}'
```
Cascading-failure hook: `kubectl -n sentinelops-demo set env deploy/inventory-service INVENTORY_DELAY_MS=4000`.

## Phase 3 — observability
```bash
curl -s 'http://localhost:30090/api/v1/targets' | jq '.data.activeTargets[] | {app:.labels.app, health}'
curl -s http://localhost:8090/api/prometheus/demo/request-rate | jq
```

## Phase 4 — chaos
```bash
./chaos/trigger.sh --list
./chaos/trigger.sh network-delay
./chaos/trigger.sh --active
kubectl -n sentinelops-demo get podchaos,networkchaos,stresschaos
```

## Phase 5 — incident memory
```bash
curl -s http://localhost:8090/api/incidents/count            # {"count":38}
curl -s -X POST 'http://localhost:8090/api/incidents/retrieve?topK=5' \
  -H 'Content-Type: application/json' \
  -d '{"serviceTypes":["network","proxy"],"symptomType":"latency","errorPatternCategory":"network-latency","description":"downstream slow, timeouts"}' | jq
./incident-memory/validate.sh
pip install -r incident-memory/requirements.txt
python incident-memory/retrieval_eval.py        # precision@1/@3, MRR against the live agent
```

## Phase 6 — agent loop
```bash
curl -s http://localhost:8090/api/agent/state | jq
curl -s -X POST 'http://localhost:8090/api/agent/respond' | jq        # full trace
curl -s http://localhost:8090/api/agent/cluster | jq                  # getClusterState
curl -s -X POST 'http://localhost:8090/api/agent/dry-run?enabled=true'
```

## Phase 7 — dashboard
Open **http://localhost:5173**, click a chaos button, watch the trace stream.
Build/typecheck: `cd dashboard && npm run build`.

## Phase 8 — scenarios
```bash
./scripts/demo.sh pod-kill
./scripts/demo.sh network-delay
./scripts/demo.sh cpu-stress
./scripts/demo.sh network-partition
```
See **[DEMO.md](DEMO.md)** for the narrated walkthrough + interview script.

---

## Test suites (no cluster needed)
```bash
# services: 6 unit tests
docker run --rm -v "$PWD/services:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B test
# agent: 32 unit tests (retrieval quality, planner, guardrails, k8s mock, ...)
docker run --rm -v "$PWD/agent:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B test
# dashboard: typecheck + build
docker run --rm -v "$PWD/dashboard:/app" -w /app node:20-alpine sh -c "npm install && npm run build"
```
