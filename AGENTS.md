# Multi-stablecoin Clearing & Final Settlement PoC (MySQL + Spring + Hardhat)

## What we're building
A backend PoC that:
1) Receives obligations (idempotent intake, tx_id unique).
2) Applies simple risk rule (Net Debit Cap).
3) Runs epoch-based multilateral netting -> net_positions.
4) Final-settles net_positions in ONE tx on local ledger contract (SettlementVault).
5) Converges operational state with retries; records outbox events.

## Repo layout
- /clearing-hub        Spring Boot 3, Java 17, Gradle
- /settlement-ledger   Hardhat + Solidity contract
- /scripts             db up/down, demo scripts
- /docs                plan/notes

## Manual step
User will add /clearing-hub scaffold via Spring Initializr ZIP.
Do NOT regenerate Spring scaffolding unless explicitly asked.

## Never commit secrets
- SETTLEMENT_VAULT_ADDRESS
- LEDGER_OPERATOR_PRIVATE_KEY
Keep only .env.example.

## Verification commands
- DB up/down:
  ./scripts/db-up.sh
  ./scripts/db-down.sh
- Spring:
  (cd clearing-hub && ./gradlew test)
  (cd clearing-hub && ./gradlew bootRun)
- Hardhat:
  (cd settlement-ledger && npm install)
  (cd settlement-ledger && npm run node)
  (cd settlement-ledger && npm run deploy:local)

## Coding rules
- Use application.yml (not application.properties).
- Use Flyway; spring.jpa.hibernate.ddl-auto must be validate.
- Amounts in KRW as long (integer).
- Use UTC timestamps.
- Use @Transactional for write flows.
- Use pessimistic locks when:
  - closing epoch
  - processing settlement instruction
- Idempotency:
  - obligations.tx_id UNIQUE
  - request_hash mismatch => 409 conflict

## Commit policy (IMPORTANT)
While working, make MULTIPLE atomic commits:
- Commit whenever a cohesive piece is done AND verified.
- Do NOT make one giant commit at the end.
- Do NOT mix unrelated concerns in one commit.
- Use clear messages (chore:, db:, feat:, fix:, docs:).
- After each commit, run a relevant verification command and note it.
- At the end of each milestone:
  - print `git log --oneline -n 20`
  - summarize what was verified
