#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TX_PREFIX="tx-demo-retry-$(date +%s)"

post() {
  local payload="$1"
  curl -sS -i -X POST "${API_BASE_URL}/obligations" \
    -H 'Content-Type: application/json' \
    -d "${payload}"
}

echo "[demo_failure_retry] API_BASE_URL=${API_BASE_URL}"
echo "Precondition for this scenario: run clearing-hub with broken ledger connectivity (example: Hardhat node stopped, or invalid SETTLEMENT_VAULT_ADDRESS / LEDGER_OPERATOR_PRIVATE_KEY)."
echo "When precondition is met, settlement should move CREATED -> RETRYING and eventually FAILED after max attempts."

echo "[1/3] A -> B 90000"
post "$(printf '{"txId":"%s-1","payer":"A","payee":"B","payAsset":"KRW","amount":90000}' "${TX_PREFIX}")"

echo
echo "[2/3] B -> C 30000"
post "$(printf '{"txId":"%s-2","payer":"B","payee":"C","payAsset":"KRW","amount":30000}' "${TX_PREFIX}")"

echo
echo "[3/3] C -> A 60000"
post "$(printf '{"txId":"%s-3","payer":"C","payee":"A","payAsset":"KRW","amount":60000}' "${TX_PREFIX}")"

echo
echo "Next checks (after epoch closes):"
echo "- SQL (find target epoch id):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,epoch_no,status,opened_at,closed_at from epochs order by id desc limit 5;\""
echo "- API (replace {epochId}; expect status NETTED or FAILED while retries are happening):"
echo "  curl -sS ${API_BASE_URL}/epochs/{epochId}"
echo "- SQL (watch retries):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,epoch_id,status,tx_hash,attempt_count,last_error,next_retry_at from settlement_instructions where epoch_id={epochId};\""
echo "- SQL (expected in failure demo): attempt_count increments, last_error populated, final status FAILED when attempts exceed max."
