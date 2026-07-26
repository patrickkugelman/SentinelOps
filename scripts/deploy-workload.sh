#!/usr/bin/env bash
# PHASE 2 — build the demo service images, load them into kind, and install the
# sentinelops-demo Helm chart. Idempotent: re-run to redeploy after code changes.
#
#   ./scripts/deploy-workload.sh            # build + load + helm upgrade --install
#   ./scripts/deploy-workload.sh --no-build # skip image build (reuse loaded images)
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env

need docker
need kind
need kubectl
need helm

cluster_exists || die "kind cluster '${KIND_CLUSTER_NAME}' not found. Run scripts/kind-up.sh first."

IMAGE_TAG="${IMAGE_TAG:-dev}"
SERVICES=(inventory-service payment-service order-service)
BUILD=1
[[ "${1:-}" == "--no-build" ]] && BUILD=0

if [[ "$BUILD" -eq 1 ]]; then
  section "Building service images (tag: ${IMAGE_TAG})"
  for svc in "${SERVICES[@]}"; do
    log "docker build sentinelops/${svc}:${IMAGE_TAG}"
    docker build -t "sentinelops/${svc}:${IMAGE_TAG}" "${REPO_ROOT}/services/${svc}"
  done
  ok "Images built."
fi

section "Loading images into kind cluster '${KIND_CLUSTER_NAME}'"
for svc in "${SERVICES[@]}"; do
  log "kind load docker-image sentinelops/${svc}:${IMAGE_TAG}"
  kind load docker-image "sentinelops/${svc}:${IMAGE_TAG}" --name "${KIND_CLUSTER_NAME}"
done
ok "Images loaded."

section "helm upgrade --install sentinelops-demo"
helm upgrade --install sentinelops-demo "${REPO_ROOT}/helm/sentinelops-demo" \
  --namespace "${DEMO_NAMESPACE}" --create-namespace \
  --set image.tag="${IMAGE_TAG}" \
  --wait --timeout 5m

section "Rollout status"
kubectl -n "${DEMO_NAMESPACE}" get pods -o wide
ok "Demo workload deployed."

cat <<EOF

Verify an end-to-end checkout (order-service on host port 30080):
  curl -s -X POST http://localhost:30080/orders/checkout \\
       -H 'Content-Type: application/json' \\
       -d '{"sku":"SKU-LAPTOP","quantity":1}'

Or run the smoke test:
  ./scripts/smoke-demo.sh
EOF
