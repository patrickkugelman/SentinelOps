#!/usr/bin/env bash
# Run a full end-to-end demo scenario: inject chaos, let the agent respond, and
# print the reasoning trace. Watch it live on the dashboard at the same time.
#
#   ./scripts/demo.sh pod-kill
#   ./scripts/demo.sh network-delay
#   ./scripts/demo.sh cpu-stress
#   ./scripts/demo.sh network-partition
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

AGENT="${AGENT_BASE_URL:-http://localhost:8090}"
SCENARIO="${1:-pod-kill}"
need curl

# scenario -> target service | anomaly type | needs traffic?
case "$SCENARIO" in
  pod-kill)          TARGET=inventory-service; TYPE=crash;               LOAD=0 ;;
  network-delay)     TARGET=inventory-service; TYPE=latency;             LOAD=1 ;;
  cpu-stress)        TARGET=payment-service;   TYPE=resource-exhaustion; LOAD=1 ;;
  network-partition) TARGET=order-service;     TYPE=error-rate;          LOAD=1 ;;
  *) die "unknown scenario '$SCENARIO' (pod-kill|network-delay|cpu-stress|network-partition)" ;;
esac

section "SentinelOps demo — ${SCENARIO}"
curl -fsS "${AGENT}/api/agent/state" >/dev/null 2>&1 || die "agent not reachable at ${AGENT} (start it first)"
ok "agent reachable at ${AGENT}"
log "Open the dashboard (http://localhost:5173) to watch this live."

LOAD_PID=""
if [[ "$LOAD" -eq 1 ]]; then
  log "Generating checkout traffic so the failure is observable ..."
  ( RPS=8 DURATION=120 bash "${SENTINEL_LIB_DIR}/load.sh" >/dev/null 2>&1 ) &
  LOAD_PID=$!
  sleep 4
fi

section "1) Inject chaos: ${SCENARIO} on ${TARGET}"
curl -fsS -X POST "${AGENT}/api/chaos/experiments/${SCENARIO}" \
     -H 'Content-Type: application/json' -d '{"durationSeconds":60}' \
  | node -e 'const h=JSON.parse(require("fs").readFileSync(0));console.log(`  injected ${h.experimentId} -> ${h.target} (${h.name})`)' 2>/dev/null \
  || warn "chaos trigger failed (is Chaos Mesh installed?)"

log "Letting the anomaly manifest ..."
sleep 6

section "2) Agent responds"
curl -fsS -X POST "${AGENT}/api/agent/respond?service=${TARGET}&type=${TYPE}" \
  | node -e '
    const t=JSON.parse(require("fs").readFileSync(0));
    const p=t.precedents&&t.precedents[0];
    console.log("  anomaly   :", t.anomaly ? `${t.anomaly.type} on ${t.anomaly.service}` : "none");
    if(p) console.log("  precedent :", `${p.title}\n              ${p.sourceUrl}`);
    if(t.decision) console.log("  decision  :", `${t.decision.action} ${t.decision.targetService||""}`);
    if(t.decision) console.log("  why       :", t.decision.justification);
    if(t.result) console.log("  result    :", `${t.result.executed?"EXECUTED":(t.result.dryRun?"DRY-RUN":"no-op")} — ${t.result.message}`);
    if(t.recordedIncidentId) console.log("  recorded  :", t.recordedIncidentId, "(written back to memory)");
  ' 2>/dev/null || err "agent respond failed"

echo
ok "Scenario complete. The dashboard shows the trace + metrics recovering."
[[ -n "$LOAD_PID" ]] && { kill "$LOAD_PID" >/dev/null 2>&1 || true; }
