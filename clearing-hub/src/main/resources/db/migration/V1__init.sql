CREATE TABLE participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    participant_code VARCHAR(32) NOT NULL,
    ledger_address VARCHAR(42) NOT NULL,
    net_debit_cap_krw BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_participants_code (participant_code),
    UNIQUE KEY uk_participants_ledger_address (ledger_address),
    CONSTRAINT chk_participants_cap_non_negative CHECK (net_debit_cap_krw >= 0)
);

CREATE TABLE epochs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    epoch_no BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    opened_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_epochs_epoch_no (epoch_no)
);

CREATE TABLE obligations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tx_id VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    debtor_participant_id BIGINT NOT NULL,
    creditor_participant_id BIGINT NOT NULL,
    amount_krw BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    epoch_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_obligations_tx_id (tx_id),
    KEY idx_obligations_epoch_status (epoch_id, status),
    KEY idx_obligations_debtor (debtor_participant_id),
    KEY idx_obligations_creditor (creditor_participant_id),
    KEY idx_obligations_created_at (created_at),
    CONSTRAINT fk_obligations_debtor_participant FOREIGN KEY (debtor_participant_id) REFERENCES participants (id),
    CONSTRAINT fk_obligations_creditor_participant FOREIGN KEY (creditor_participant_id) REFERENCES participants (id),
    CONSTRAINT fk_obligations_epoch FOREIGN KEY (epoch_id) REFERENCES epochs (id),
    CONSTRAINT chk_obligations_positive_amount CHECK (amount_krw > 0),
    CONSTRAINT chk_obligations_distinct_participants CHECK (debtor_participant_id <> creditor_participant_id)
);

CREATE TABLE net_positions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    epoch_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    net_amount_krw BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_net_positions_epoch_participant (epoch_id, participant_id),
    KEY idx_net_positions_participant (participant_id),
    CONSTRAINT fk_net_positions_epoch FOREIGN KEY (epoch_id) REFERENCES epochs (id),
    CONSTRAINT fk_net_positions_participant FOREIGN KEY (participant_id) REFERENCES participants (id)
);

CREATE TABLE settlement_instructions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    epoch_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    tx_hash VARCHAR(66) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_instructions_epoch (epoch_id),
    KEY idx_settlement_instructions_status_retry (status, next_retry_at),
    CONSTRAINT fk_settlement_instructions_epoch FOREIGN KEY (epoch_id) REFERENCES epochs (id),
    CONSTRAINT chk_settlement_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_outbox_events_status_available (status, available_at),
    KEY idx_outbox_events_aggregate (aggregate_type, aggregate_id),
    KEY idx_outbox_events_created_at (created_at)
);
