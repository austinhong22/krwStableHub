#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
AUTO_RUN="${1:-}"

run_step() {
  local title="$1"
  local script_path="$2"

  echo
  echo "============================================================"
  echo "${title}"
  echo "============================================================"

  if [[ "${AUTO_RUN}" == "--yes" ]]; then
    "${script_path}"
    return
  fi

  read -r -p "Run this step now? [y/N] " answer
  case "${answer}" in
    y|Y|yes|YES)
      "${script_path}"
      ;;
    *)
      echo "Skipped ${title}."
      ;;
  esac
}

echo "KRW Stable Hub Portfolio Demo (guided)"
echo "API_BASE_URL=${API_BASE_URL}"
echo
echo "Before running scenarios, ensure these are already up:"
echo "1) ./scripts/db-up.sh"
echo "2) (cd settlement-ledger && npm run node)"
echo "3) (cd settlement-ledger && npm run deploy:local)"
echo "4) export SETTLEMENT_VAULT_ADDRESS=<deployed-address>"
echo "5) export LEDGER_OPERATOR_PRIVATE_KEY=<hardhat-account-private-key>"
echo "6) (cd clearing-hub && ./gradlew bootRun)"
echo
echo "For failure-retry scenario, restart clearing-hub with intentionally broken ledger settings or stop Hardhat first."

run_step "Scenario 1: Idempotency" "${ROOT_DIR}/scripts/demo_idempotency.sh"
run_step "Scenario 2: Netting + Single On-chain Settlement Tx" "${ROOT_DIR}/scripts/demo_netting_1tx.sh"
run_step "Scenario 3: Risk Cap HOLD" "${ROOT_DIR}/scripts/demo_risk_cap_hold.sh"
run_step "Scenario 4: Failure + Retry" "${ROOT_DIR}/scripts/demo_failure_retry.sh"

echo
echo "Guided demo finished. Use each script's printed SQL and /epochs/{id} checks for reviewer evidence."
