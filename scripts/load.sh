#!/usr/bin/env bash
# Simple steady checkout traffic generator so metrics move during a demo.
# Not a benchmark — just enough load to make latency/error-rate charts live.
#
#   ./scripts/load.sh                 # ~5 req/s for 300s across the catalog
#   RPS=10 DURATION=120 ./scripts/load.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BASE="${ORDER_BASE_URL:-http://localhost:30080}"
RPS="${RPS:-5}"
DURATION="${DURATION:-300}"
SKUS=(SKU-LAPTOP SKU-PHONE SKU-HEADSET SKU-KEYBOARD SKU-MOUSE)
need curl

interval="$(awk "BEGIN{print 1/${RPS}}")"
end=$(( $(date +%s) + DURATION ))
ok=0; bad=0
log "Generating ~${RPS} checkout/s for ${DURATION}s against ${BASE} (Ctrl-C to stop)"
while [[ "$(date +%s)" -lt "$end" ]]; do
  sku="${SKUS[$((RANDOM % ${#SKUS[@]}))]}"
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BASE}/orders/checkout" \
          -H 'Content-Type: application/json' -d "{\"sku\":\"${sku}\",\"quantity\":1}" || echo 000)"
  if [[ "$code" == "200" ]]; then ok=$((ok+1)); else bad=$((bad+1)); fi
  printf "\r  ok=%d  errors=%d  (last=%s)   " "$ok" "$bad" "$code"
  sleep "$interval"
done
echo
ok "Done. ok=${ok} errors=${bad}"
