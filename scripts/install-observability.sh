#!/usr/bin/env bash
# PHASE 3 — install standalone Prometheus (and optionally Grafana) into the kind
# cluster. Prometheus discovers the demo pods via their prometheus.io/* annotations.
#
#   ./scripts/install-observability.sh                 # Prometheus only
#   ./scripts/install-observability.sh --with-grafana  # + Grafana on :30030
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env

need kubectl
cluster_exists || die "kind cluster '${KIND_CLUSTER_NAME}' not found. Run scripts/kind-up.sh first."

OBS_DIR="${REPO_ROOT}/infra/observability"

section "Installing Prometheus"
kubectl apply -f "${OBS_DIR}/prometheus/namespace.yaml"
kubectl apply -f "${OBS_DIR}/prometheus/rbac.yaml"
kubectl apply -f "${OBS_DIR}/prometheus/configmap.yaml"
kubectl apply -f "${OBS_DIR}/prometheus/deployment.yaml"
kubectl apply -f "${OBS_DIR}/prometheus/service.yaml"

log "Waiting for Prometheus rollout ..."
kubectl -n sentinelops-observability rollout status deploy/prometheus --timeout=120s
ok "Prometheus is up -> http://localhost:30090"

if [[ "${1:-}" == "--with-grafana" ]]; then
  section "Installing Grafana (optional)"
  kubectl apply -f "${OBS_DIR}/grafana/grafana.yaml"
  kubectl -n sentinelops-observability rollout status deploy/grafana --timeout=120s
  ok "Grafana is up -> http://localhost:30030 (anonymous viewer; admin/sentinelops)"
fi

cat <<'EOF'

Verify scraping (after the demo workload is deployed):
  # targets should include the 3 demo pods in state "up":
  curl -s 'http://localhost:30090/api/v1/targets' | jq '.data.activeTargets[] | {app: .labels.app, health}'

  # request rate by service:
  curl -s --get 'http://localhost:30090/api/v1/query' \
       --data-urlencode 'query=sum by (app) (rate(http_server_requests_seconds_count{namespace="sentinelops-demo"}[1m]))' | jq
EOF
