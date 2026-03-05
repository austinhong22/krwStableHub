#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TX_PREFIX="tx-demo-net-$(date +%s)"

post() {
  local payload="$1"
  curl -sS -i -X POST "${API_BASE_URL}/obligations" \
    -H 'Content-Type: application/json' \
    -d "${payload}"
}

echo "[demo_netting_1tx] API_BASE_URL=${API_BASE_URL}"
echo "Submitting 3 ACCEPTED obligations in one epoch window."

echo "[1/3] A -> B 70000"
post "$(printf '{"txId":"%s-1","payer":"A","payee":"B","payAsset":"KRW","amount":70000}' "${TX_PREFIX}")"

echo
echo "[2/3] B -> C 20000"
post "$(printf '{"txId":"%s-2","payer":"B","payee":"C","payAsset":"KRW","amount":20000}' "${TX_PREFIX}")"

echo
echo "[3/3] C -> A 10000"
post "$(printf '{"txId":"%s-3","payer":"C","payee":"A","payAsset":"KRW","amount":10000}' "${TX_PREFIX}")"

echo
echo "Next checks (wait until epoch closes; default window is 60 seconds):"
echo "- SQL (locate epoch ids):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,epoch_no,status,opened_at,closed_at from epochs order by id desc limit 5;\""
echo "- API (replace {epochId}, expected eventually: status=SETTLED, settlementStatus=SETTLED, txHash not null):"
echo "  curl -sS ${API_BASE_URL}/epochs/{epochId}"
echo "- SQL (net positions for that epoch):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select epoch_id,participant_id,net_amount_krw from net_positions where epoch_id={epochId} order by id;\""
echo "- SQL (single settlement instruction, one tx):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,epoch_id,status,tx_hash,attempt_count,last_error,next_retry_at from settlement_instructions where epoch_id={epochId};\""
echo "- SQL (outbox event):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,aggregate_id,event_type,status,available_at from outbox_events where aggregate_type='EPOCH' and aggregate_id={epochId} order by id;\""
