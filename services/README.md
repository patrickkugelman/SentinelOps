# Demo workload — `services/`

Three Spring Boot (Java 21) microservices that form a realistic checkout flow,
plus Postgres as their datastore. Built and deployed in **Phase 2**.

```
order-service      REST /checkout — orchestrates a purchase
   │  calls
   ├─▶ inventory-service   reserve/confirm stock
   └─▶ payment-service     authorize/capture payment
```

Why three services calling each other over REST: it creates genuine
**cascading-failure** modes the agent can detect and remediate, e.g.
`inventory-service` slow → `order-service` thread pool exhausts → checkout 5xx.

Each service exposes Prometheus metrics at `/actuator/prometheus` (Phase 3) and
is packaged as a container image loaded into kind (`kind load docker-image`).

> Populated in Phase 2.
