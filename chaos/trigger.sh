#!/usr/bin/env bash
# Convenience wrapper around the agent's chaos API.
#   ./chaos/trigger.sh --list
#   ./chaos/trigger.sh network-delay                 # default target + duration
#   ./chaos/trigger.sh cpu-stress payment-service 90 # target + durationSeconds
#   ./chaos/trigger.sh --active
#   ./chaos/trigger.sh --stop <name>
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../scripts" && pwd)/lib.sh"

AGENT="${AGENT_BASE_URL:-http://localhost:8090}"
need curl

case "${1:-}" in
  --list|"")
    curl -fsS "${AGENT}/api/chaos/experiments" | jq -r '.[] | "\(.id)\t[\(.kind)] target=\(.defaultTarget) — \(.description)"' 2>/dev/null \
      || curl -fsS "${AGENT}/api/chaos/experiments"
    ;;
  --active)
    curl -fsS "${AGENT}/api/chaos/active" | jq 2>/dev/null || curl -fsS "${AGENT}/api/chaos/active"
    ;;
  --stop)
    [[ -n "${2:-}" ]] || die "usage: trigger.sh --stop <name>"
    curl -fsS -X DELETE "${AGENT}/api/chaos/active/$2" | jq 2>/dev/null || true
    ;;
  *)
    id="$1"; target="${2:-}"; dur="${3:-}"
    body="{}"
    if [[ -n "$target" && -n "$dur" ]]; then body="{\"target\":\"${target}\",\"durationSeconds\":${dur}}"
    elif [[ -n "$target" ]]; then body="{\"target\":\"${target}\"}"; fi
    log "Triggering ${id} ${body}"
    curl -fsS -X POST "${AGENT}/api/chaos/experiments/${id}" \
         -H 'Content-Type: application/json' -d "$body" | jq 2>/dev/null \
      || err "trigger failed (is the agent running on ${AGENT}?)"
    ;;
esac
