#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TX_ID="tx-demo-idem-$(date +%s)"

post() {
  local payload="$1"
  curl -sS -i -X POST "${API_BASE_URL}/obligations" \
    -H 'Content-Type: application/json' \
    -d "${payload}"
}

echo "[demo_idempotency] API_BASE_URL=${API_BASE_URL}"
echo "[1/3] First request (expected 202, status ACCEPTED or HELD depending on risk state)"
PAYLOAD_SAME="$(printf '{"txId":"%s","payer":"A","payee":"B","payAsset":"KRW","amount":50000}' "${TX_ID}")"
post "${PAYLOAD_SAME}"

echo
echo "[2/3] Same txId + same payload (expected idempotent replay: 202 with same obligation)"
post "${PAYLOAD_SAME}"

echo
echo "[3/3] Same txId + different payload (expected 409 IDEMPOTENCY_CONFLICT)"
PAYLOAD_CONFLICT="$(printf '{"txId":"%s","payer":"A","payee":"B","payAsset":"KRW","amount":51000}' "${TX_ID}")"
post "${PAYLOAD_CONFLICT}" || true

echo
echo "Next checks:"
echo "- SQL (exactly one row for tx_id):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,tx_id,request_hash,status,epoch_id,amount_krw from obligations where tx_id='${TX_ID}';\""
echo "- SQL (find latest epoch id):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,epoch_no,status,opened_at,closed_at from epochs order by id desc limit 5;\""
echo "- API (replace {epochId}):"
echo "  curl -sS ${API_BASE_URL}/epochs/{epochId}"
