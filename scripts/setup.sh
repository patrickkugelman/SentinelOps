#!/usr/bin/env bash
# =============================================================================
# SentinelOps — one-shot local bring-up.
#
# This is the "one script" from the brief. It runs each phase's installer in
# order and is safe to re-run. Phases not yet implemented are skipped with a
# clear notice (see the PHASES array below as the project fills in).
#
#   ./scripts/setup.sh            # bring up everything available
#   ./scripts/setup.sh --infra    # only the out-of-cluster infra (Postgres)
#   ./scripts/setup.sh --cluster  # only create the kind cluster
# =============================================================================
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

MODE="all"
[[ "${1:-}" == "--infra"   ]] && MODE="infra"
[[ "${1:-}" == "--cluster" ]] && MODE="cluster"

run_step() { # name script
  local name="$1" script="$2"
  if [[ -x "$script" || -f "$script" ]]; then
    section "$name"
    bash "$script"
  else
    warn "SKIP: $name — '$script' not implemented yet (added in a later phase)."
  fi
}

"${SENTINEL_LIB_DIR}/preflight.sh"

# ---- Out-of-cluster infra: Postgres + pgvector ----
if [[ "$MODE" == "all" || "$MODE" == "infra" ]]; then
  section "Postgres + pgvector (docker compose)"
  [[ -f "${REPO_ROOT}/.env" ]] || { cp "${REPO_ROOT}/.env.example" "${REPO_ROOT}/.env"; warn "Created .env from .env.example — set OPENAI_API_KEY before running the agent."; }
  ( cd "${REPO_ROOT}" && docker compose up -d )
  ok "Postgres is starting. Check: docker compose ps"
fi
[[ "$MODE" == "infra" ]] && exit 0

# ---- Cluster ----
if [[ "$MODE" == "all" || "$MODE" == "cluster" ]]; then
  run_step "kind cluster"        "${SENTINEL_LIB_DIR}/kind-up.sh"
fi
[[ "$MODE" == "cluster" ]] && exit 0

# ---- Later phases (installers land here as the project progresses) ----
run_step "Observability (Prometheus)" "${SENTINEL_LIB_DIR}/install-observability.sh"
run_step "Chaos Mesh"                  "${SENTINEL_LIB_DIR}/install-chaos-mesh.sh"
run_step "Demo workload (Helm)"        "${SENTINEL_LIB_DIR}/deploy-workload.sh"

section "Done"
ok "SentinelOps local environment is up (for the phases implemented so far)."
cat <<'EOF'

Next:
  docker compose ps                 # Postgres health
  kubectl get pods -A               # cluster state
  See README.md "Project status" for what each phase delivers.
EOF
