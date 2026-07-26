#!/usr/bin/env bash
# Create the SentinelOps kind cluster (idempotent) and the demo namespace.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env

need docker
need kind
need kubectl

section "kind cluster: ${KIND_CLUSTER_NAME}"

if cluster_exists; then
  ok "Cluster '${KIND_CLUSTER_NAME}' already exists — reusing."
else
  log "Creating cluster from infra/kind/kind-config.yaml ..."
  kind create cluster --config "${REPO_ROOT}/infra/kind/kind-config.yaml" --wait 120s
  ok "Cluster created."
fi

log "Setting kubectl context to kind-${KIND_CLUSTER_NAME}"
kubectl config use-context "kind-${KIND_CLUSTER_NAME}" >/dev/null

log "Ensuring namespace '${DEMO_NAMESPACE}' exists"
kubectl create namespace "${DEMO_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
# Label so the agent's guardrail can positively identify its sandbox.
kubectl label namespace "${DEMO_NAMESPACE}" sentinelops.io/sandbox=true --overwrite >/dev/null

ok "Cluster ready. Nodes:"
kubectl get nodes -o wide
