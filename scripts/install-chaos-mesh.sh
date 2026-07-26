#!/usr/bin/env bash
# PHASE 4 — install Chaos Mesh into the kind cluster via Helm, configured for
# kind's containerd runtime. Idempotent.
#
#   ./scripts/install-chaos-mesh.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env

need kubectl
need helm
cluster_exists || die "kind cluster '${KIND_CLUSTER_NAME}' not found. Run scripts/kind-up.sh first."

CHAOS_VERSION="${CHAOS_MESH_VERSION:-2.6.3}"
CHAOS_NS="chaos-mesh"

section "Installing Chaos Mesh ${CHAOS_VERSION}"

if ! helm repo list 2>/dev/null | grep -q 'chaos-mesh'; then
  helm repo add chaos-mesh https://charts.chaos-mesh.org
fi
helm repo update chaos-mesh >/dev/null

# kind uses containerd; the chaos-daemon needs its socket to inject faults.
helm upgrade --install chaos-mesh chaos-mesh/chaos-mesh \
  --namespace "${CHAOS_NS}" --create-namespace \
  --version "${CHAOS_VERSION}" \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
  --set dashboard.create=true \
  --wait --timeout 5m

section "Rollout"
kubectl -n "${CHAOS_NS}" get pods

ok "Chaos Mesh installed."
cat <<'EOF'

Trigger experiments two ways:

  1) Via the agent API (what the dashboard uses):
       curl -s http://localhost:8090/api/chaos/experiments | jq
       curl -s -X POST http://localhost:8090/api/chaos/experiments/network-delay \
            -H 'Content-Type: application/json' -d '{"durationSeconds":60}'
       curl -s http://localhost:8090/api/chaos/active | jq

  2) Directly with kubectl (templates live in agent/src/main/resources/chaos/):
       kubectl apply -f agent/src/main/resources/chaos/pod-kill.yaml
       kubectl -n sentinelops-demo get podchaos,networkchaos,stresschaos
EOF
