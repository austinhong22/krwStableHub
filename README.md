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

## Placeholders

- [ ] Add Spring Initializr scaffold under `clearing-hub`.
- [ ] Add Flyway migration baseline and application configuration.
- [ ] Add Hardhat project setup and local deploy scripts.
- [ ] Add end-to-end demo flow documentation.
