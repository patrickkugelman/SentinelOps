#!/usr/bin/env bash
# Tail logs for a demo service (or all of them). Convenience for demos.
#   ./scripts/logs.sh order          # tail order-service
#   ./scripts/logs.sh                 # tail all demo pods
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env
need kubectl

if [[ -n "${1:-}" ]]; then
  svc="$1"
  [[ "$svc" == *-service ]] || svc="${svc}-service"
  exec kubectl -n "${DEMO_NAMESPACE}" logs -f -l "app.kubernetes.io/name=${svc}" --tail=100 --prefix
fi
exec kubectl -n "${DEMO_NAMESPACE}" logs -f -l sentinelops.io/component=demo-service --tail=50 --prefix --max-log-requests=10
