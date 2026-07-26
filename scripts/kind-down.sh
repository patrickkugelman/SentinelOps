#!/usr/bin/env bash
# Delete the SentinelOps kind cluster. Leaves the Postgres compose volume alone.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env

need kind
section "Deleting kind cluster: ${KIND_CLUSTER_NAME}"

if cluster_exists; then
  kind delete cluster --name "${KIND_CLUSTER_NAME}"
  ok "Cluster deleted."
else
  warn "Cluster '${KIND_CLUSTER_NAME}' does not exist — nothing to do."
fi
