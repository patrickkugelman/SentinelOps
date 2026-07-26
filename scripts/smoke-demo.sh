#!/usr/bin/env bash
# End-to-end smoke test for the demo workload. Exercises a happy-path checkout
# and an out-of-stock failure, asserting the expected statuses.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BASE="${ORDER_BASE_URL:-http://localhost:30080}"
need curl

section "Smoke test against ${BASE}"

# ---- wait for order-service to be reachable ----
log "Waiting for order-service health ..."
for i in $(seq 1 30); do
  if curl -fsS "${BASE}/actuator/health" >/dev/null 2>&1; then ok "order-service is up"; break; fi
  [[ "$i" -eq 30 ]] && die "order-service not reachable at ${BASE} after 60s"
  sleep 2
done

pass=0 fail=0
check_json() { # description  jq-filter  expected  json
  local desc="$1" filter="$2" expected="$3" json="$4" actual
  actual="$(printf '%s' "$json" | jq -r "$filter" 2>/dev/null || echo '<parse-error>')"
  if [[ "$actual" == "$expected" ]]; then ok "$desc ($actual)"; pass=$((pass+1));
  else err "$desc — expected '$expected', got '$actual'"; echo "    body: $json"; fail=$((fail+1)); fi
}

# ---- 1. happy path ----
section "1) Happy-path checkout"
resp="$(curl -sS -X POST "${BASE}/orders/checkout" -H 'Content-Type: application/json' \
        -d '{"sku":"SKU-LAPTOP","quantity":1}')"
check_json "order completed" '.status' "COMPLETED" "$resp"
check_json "amount charged"  '.amount'  "1299.00"  "$resp"

# ---- 2. oversell -> failure + compensation ----
section "2) Oversell is rejected (order-service returns 502 with an error body)"
code="$(curl -sS -o /tmp/sentinel_smoke_body -w '%{http_code}' -X POST "${BASE}/orders/checkout" \
        -H 'Content-Type: application/json' -d '{"sku":"SKU-MOUSE","quantity":100000}')"
body="$(cat /tmp/sentinel_smoke_body)"; rm -f /tmp/sentinel_smoke_body
if [[ "$code" == "502" ]] && printf '%s' "$body" | grep -q "checkout failed"; then
  ok "oversell rejected (HTTP $code)"; pass=$((pass+1))
else
  err "oversell not rejected as expected — HTTP $code, body: $body"; fail=$((fail+1))
fi

echo
section "Result"
printf "passed: %d  failed: %d\n" "$pass" "$fail"
[[ "$fail" -eq 0 ]] || exit 1
ok "Smoke test passed."
