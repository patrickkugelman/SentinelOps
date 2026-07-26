#!/usr/bin/env bash
# Shared helpers for SentinelOps scripts. `source` this; don't execute it.
# shellcheck disable=SC2034  # colors are used by sourcing scripts

set -euo pipefail

# ---- Resolve repo root regardless of where the script is invoked from ----
SENTINEL_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SENTINEL_LIB_DIR}/.." && pwd)"

# ---- Colors (disabled when not a TTY) ----
if [[ -t 1 ]]; then
  C_RESET='\033[0m'; C_BOLD='\033[1m'
  C_RED='\033[31m'; C_GREEN='\033[32m'; C_YELLOW='\033[33m'; C_BLUE='\033[34m'; C_CYAN='\033[36m'
else
  C_RESET=''; C_BOLD=''; C_RED=''; C_GREEN=''; C_YELLOW=''; C_BLUE=''; C_CYAN=''
fi

log()   { printf "${C_CYAN}▶${C_RESET} %s\n" "$*"; }
ok()    { printf "${C_GREEN}✓${C_RESET} %s\n" "$*"; }
warn()  { printf "${C_YELLOW}!${C_RESET} %s\n" "$*" >&2; }
err()   { printf "${C_RED}✗ %s${C_RESET}\n" "$*" >&2; }
die()   { err "$*"; exit 1; }
section() { printf "\n${C_BOLD}${C_BLUE}== %s ==${C_RESET}\n" "$*"; }

# ---- Load .env if present (export all vars) ----
load_env() {
  if [[ -f "${REPO_ROOT}/.env" ]]; then
    set -a; # shellcheck disable=SC1091
    source "${REPO_ROOT}/.env"; set +a
  else
    warn ".env not found — copy .env.example to .env before running for real."
  fi
  # Defaults so scripts work even without .env.
  KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-sentinelops}"
  DEMO_NAMESPACE="${DEMO_NAMESPACE:-sentinelops-demo}"
}

# ---- Require a command on PATH ----
need() {
  command -v "$1" >/dev/null 2>&1 || die "required tool '$1' not found on PATH. See README prerequisites."
}

# ---- True if the kind cluster already exists ----
cluster_exists() {
  kind get clusters 2>/dev/null | grep -qx "${KIND_CLUSTER_NAME:-sentinelops}"
}
