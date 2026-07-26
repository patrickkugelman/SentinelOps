#!/usr/bin/env bash
# Tear down the whole local environment.
#   ./scripts/teardown.sh          # delete cluster, stop Postgres (keep data)
#   ./scripts/teardown.sh --wipe   # also delete the Postgres data volume
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_env

section "Tearing down SentinelOps"

bash "${SENTINEL_LIB_DIR}/kind-down.sh" || true

if [[ "${1:-}" == "--wipe" ]]; then
  warn "Wiping Postgres data volume."
  ( cd "${REPO_ROOT}" && docker compose down -v )
else
  ( cd "${REPO_ROOT}" && docker compose down )
  ok "Postgres stopped (data volume 'sentinelops-pgdata' preserved)."
fi
ok "Teardown complete."
