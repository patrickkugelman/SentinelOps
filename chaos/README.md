# Chaos — `chaos/`

Chaos Mesh is installed into the kind cluster by
[`scripts/install-chaos-mesh.sh`](../scripts/install-chaos-mesh.sh).

The four pre-built experiments are **classpath templates in the agent** —
[`agent/src/main/resources/chaos/`](../agent/src/main/resources/chaos/) — which
are the single source of truth. They are valid, directly `kubectl apply`-able CRs
*and* are parametrized at runtime by the agent (target service, duration).

| id                  | Kind         | Default target     | Symptom              | Expected effect |
|---------------------|--------------|--------------------|----------------------|-----------------|
| `pod-kill`          | PodChaos     | inventory-service  | crash                | Pod dies + reschedules; brief blip |
| `network-delay`     | NetworkChaos | inventory-service  | latency              | Checkout timeouts cascade from slow inventory |
| `network-partition` | NetworkChaos | order-service      | error-rate           | order↔inventory cut; checkout fails fast |
| `cpu-stress`        | StressChaos  | payment-service    | resource-exhaustion  | Payment latency/errors climb |

## Triggering

**Via the agent API** (what the dashboard uses):
```bash
curl -s http://localhost:8090/api/chaos/experiments | jq          # catalog
curl -s -X POST http://localhost:8090/api/chaos/experiments/network-delay \
     -H 'Content-Type: application/json' -d '{"durationSeconds":60}'
curl -s http://localhost:8090/api/chaos/active | jq               # what's running
curl -s -X DELETE http://localhost:8090/api/chaos/active/<name>   # stop one
```
or the helper: `./chaos/trigger.sh network-delay` / `./chaos/trigger.sh --list`.

**Directly with kubectl:**
```bash
kubectl apply -f agent/src/main/resources/chaos/pod-kill.yaml
kubectl -n sentinelops-demo get podchaos,networkchaos,stresschaos
kubectl -n sentinelops-demo delete networkchaos --all
```

## Safety
The agent only ever creates/deletes chaos in the `sentinelops-demo` namespace
(`chaos.allowed-namespace`); any other namespace is refused with `403`.
