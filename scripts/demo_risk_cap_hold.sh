#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TX_PREFIX="tx-demo-risk-$(date +%s)"

post() {
  local payload="$1"
  curl -sS -i -X POST "${API_BASE_URL}/obligations" \
    -H 'Content-Type: application/json' \
    -d "${payload}"
}

echo "[demo_risk_cap_hold] API_BASE_URL=${API_BASE_URL}"
echo "Participant A seeded net_debit_cap_krw is 200000."

echo "[1/2] Control request under cap (expected 202 with status ACCEPTED)"
post "$(printf '{"txId":"%s-ok","payer":"A","payee":"B","payAsset":"KRW","amount":50000}' "${TX_PREFIX}")"

echo
echo "[2/2] Over-cap request (expected 202 with status HELD)"
post "$(printf '{"txId":"%s-hold","payer":"A","payee":"B","payAsset":"KRW","amount":250000}' "${TX_PREFIX}")"

echo
echo "Next checks:"
echo "- SQL (confirm one ACCEPTED and one HELD):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select id,tx_id,status,amount_krw,epoch_id from obligations where tx_id like '${TX_PREFIX}%' order by id;\""
echo "- SQL (HELD obligations do not enter netting):"
echo "  docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e \"select o.tx_id,o.status,np.net_amount_krw from obligations o left join net_positions np on np.epoch_id=o.epoch_id where o.tx_id like '${TX_PREFIX}%';\""
echo "- API (replace {epochId} from SQL):"
echo "  curl -sS ${API_BASE_URL}/epochs/{epochId}"
