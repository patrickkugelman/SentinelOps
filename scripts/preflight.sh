#!/usr/bin/env bash
# Verify the host has everything SentinelOps needs, with actionable hints.
# Safe to run repeatedly. Intended to run inside WSL2 (Ubuntu) with Docker
# Desktop's WSL integration enabled.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "Preflight checks"

fail=0
check() {
  local name="$1" hint="$2"
  if command -v "$name" >/dev/null 2>&1; then
    ok "$name -> $(command -v "$name")"
  else
    err "$name missing. $hint"
    fail=1
  fi
}

check docker  "Install Docker Desktop and enable WSL2 integration for this distro."
check kubectl "curl -Lo kubectl https://dl.k8s.io/release/\$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl && sudo install kubectl /usr/local/bin/"
check kind    "go install sigs.k8s.io/kind@latest  OR  see https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
check helm    "curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash"

# Docker daemon reachable?
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    ok "docker daemon reachable"
  else
    err "docker is installed but the daemon is not reachable (start Docker Desktop / enable WSL integration)."
    fail=1
  fi
fi

# Java 21 for building the agent + services (optional at this phase).
if command -v java >/dev/null 2>&1; then
  jver="$(java -version 2>&1 | head -1)"
  if java -version 2>&1 | grep -qE '"(21|22|23|24)'; then
    ok "java: $jver"
  else
    warn "java present but not 21+ ($jver). Docker builds use JDK 21 images, so local Java is only needed for IDE/dev."
  fi
else
  warn "java not found — only needed to build services locally; Docker multi-stage builds ship their own JDK 21."
fi

echo
if [[ "$fail" -ne 0 ]]; then
  die "Preflight failed. Install the missing tools above, then re-run."
fi
ok "All required tooling present."
