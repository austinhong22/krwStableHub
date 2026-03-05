# KRW Stable Hub PoC

Portfolio-grade demo for multi-stablecoin clearing and final settlement:
- idempotent obligation intake (`tx_id` uniqueness + request hash conflict protection)
- Net Debit Cap risk control (`HELD`)
- epoch-based multilateral netting (`net_positions`)
- single on-chain settlement transaction per epoch (`SettlementVault.settle`)
- retry convergence + outbox event emission

## Architecture Summary

- `clearing-hub` (Spring Boot 3 / Java 17):
  - `POST /obligations`: intake + idempotency + risk decision (`ACCEPTED`/`HELD`)
  - scheduler closes due epochs, runs netting, creates settlement instruction
  - settlement scheduler submits one ledger tx per epoch and retries on failure
  - `GET /epochs/{id}` returns epoch status, net positions, settlement status, tx hash
- `settlement-ledger` (Hardhat + Solidity):
  - `SettlementVault` contract validates net-zero deltas and emits `Settled(epochId)`
- MySQL:
  - source-of-truth tables: `obligations`, `epochs`, `net_positions`, `settlement_instructions`, `outbox_events`

## Prerequisites

- Java 17+
- Docker + Docker Compose
- Node.js 20+ and npm
- `curl`

## Start The System (Reviewer Setup)

Open separate terminals.

1. Start MySQL:
   ```bash
   ./scripts/db-up.sh
   ```

2. Install Hardhat dependencies (first time only):
   ```bash
   (cd settlement-ledger && npm install)
   ```

3. Start local chain:
   ```bash
   (cd settlement-ledger && npm run node)
   ```

4. Deploy `SettlementVault` (new terminal):
   ```bash
   (cd settlement-ledger && npm run deploy:local)
   ```
   Copy printed `SettlementVault: <address>`.

5. Configure env vars for `clearing-hub` (new terminal):
   ```bash
   export SETTLEMENT_VAULT_ADDRESS=<deployed-address>
   export LEDGER_OPERATOR_PRIVATE_KEY=<hardhat-operator-private-key>
   ```
   Notes:
   - Keep real values local only.
   - `.env.example` is intentionally blank.

6. Start Spring Boot:
   ```bash
   (cd clearing-hub && ./gradlew bootRun)
   ```

7. Health check:
   ```bash
   curl -sS http://localhost:8080/actuator/health
   ```
   Expected: `{"status":"UP"}`

## Demo Scenarios

All demo scripts are executable and use `curl` for API actions. Each script prints exact SQL/API checks to run next.

### 1) Idempotency

```bash
./scripts/demo_idempotency.sh
```

Shows:
- same `txId` + same payload is replay-safe
- same `txId` + different payload returns conflict

### 2) Netting + 1 On-chain Tx

```bash
./scripts/demo_netting_1tx.sh
```

Shows:
- accepted obligations netted at epoch close
- one settlement instruction with tx hash
- outbox event for settled epoch

### 3) Risk Cap HOLD

```bash
./scripts/demo_risk_cap_hold.sh
```

Shows:
- over-cap obligation is `HELD`
- held items are excluded from final netting positions

### 4) Failure + Retry Convergence

Precondition: run `clearing-hub` with intentionally broken ledger connectivity (for example stop Hardhat, or use invalid settlement env vars), then run:

```bash
./scripts/demo_failure_retry.sh
```

Shows:
- settlement instruction transitions through retries
- `attempt_count` increments and `last_error` is populated
- terminal state reaches `FAILED` after max attempts

### Guided Portfolio Walkthrough

```bash
./scripts/demo_full.sh
```

Interactive by default. Use `--yes` for non-interactive run:

```bash
./scripts/demo_full.sh --yes
```

## Useful Verification Queries

Latest epochs:

```bash
docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e "select id,epoch_no,status,opened_at,closed_at from epochs order by id desc limit 5;"
```

Settlement instructions:

```bash
docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e "select id,epoch_id,status,tx_hash,attempt_count,last_error,next_retry_at from settlement_instructions order by id desc limit 10;"
```

Outbox settled events:

```bash
docker exec -i clearing-mysql mysql -uroot -proot -D clearing -e "select id,aggregate_id,event_type,status,available_at from outbox_events where aggregate_type='EPOCH' order by id desc limit 10;"
```
