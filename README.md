# KRW Stable Hub PoC

Multi-stablecoin clearing and final settlement proof of concept using MySQL, Spring Boot, and Hardhat.

## Architecture Overview

- `clearing-hub`:
  Spring Boot service for obligation intake, risk checks, epoch netting, and settlement orchestration.
- `settlement-ledger`:
  Hardhat workspace containing Solidity contracts for local final settlement (`SettlementVault`).
- `docker-compose.yml`:
  Local infrastructure for MySQL 8.4.

## Start MySQL

1. Start database:
   ```bash
   ./scripts/db-up.sh
   ```
   MySQL is exposed on local port `3307` for this project.
2. Confirm container status:
   ```bash
   docker ps --filter name=clearing-mysql
   ```
3. Stop database:
   ```bash
   ./scripts/db-down.sh
   ```

## Run Clearing Hub

1. Start MySQL first:
   ```bash
   ./scripts/db-up.sh
   ```
2. Start Spring Boot:
   ```bash
   (cd clearing-hub && ./gradlew bootRun)
   ```
3. Verify actuator health:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
   Expected response:
   ```json
   {"status":"UP"}
   ```

## Obligation Intake Examples

All examples below assume:
- app is running on `localhost:8080`
- seeded participants `A`, `B`, `C` are present

1. Submit a new obligation:
   ```bash
   curl -i -X POST http://localhost:8080/obligations \
     -H 'Content-Type: application/json' \
     -d '{
       "txId":"tx-1001",
       "payer":"A",
       "payee":"B",
       "payAsset":"KRW",
       "amount":50000
     }'
   ```
   Expected: `HTTP/1.1 202 Accepted` with `status` = `ACCEPTED` (if cap is not exceeded).

2. Repeat the same `txId` with identical payload (idempotent):
   ```bash
   curl -i -X POST http://localhost:8080/obligations \
     -H 'Content-Type: application/json' \
     -d '{
       "txId":"tx-1001",
       "payer":"A",
       "payee":"B",
       "payAsset":"KRW",
       "amount":50000
     }'
   ```
   Then verify only one row exists:
   ```bash
   docker exec -i clearing-mysql \
     mysql -uroot -proot -D clearing \
     -e "select tx_id, count(*) as cnt from obligations where tx_id='tx-1001' group by tx_id;"
   ```
   Expected: `cnt = 1`.

3. Trigger `HELD` by lowering payer cap and sending a large obligation:
   ```bash
   docker exec -i clearing-mysql \
     mysql -uroot -proot -D clearing \
     -e "update participants set net_debit_cap_krw=10000 where participant_code='A';"
   ```
   ```bash
   curl -i -X POST http://localhost:8080/obligations \
     -H 'Content-Type: application/json' \
     -d '{
       "txId":"tx-1002",
       "payer":"A",
       "payee":"B",
       "payAsset":"KRW",
       "amount":50000
     }'
   ```
   Expected: `HTTP/1.1 202 Accepted` with `status` = `HELD`.
