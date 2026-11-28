CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL
);

CREATE TABLE bank_account (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    bank_name TEXT   NOT NULL,
    currency  TEXT   NOT NULL CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_bank_account_user ON bank_account (user_id);

CREATE TABLE bank_statement (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bank_account_id       BIGINT NOT NULL REFERENCES bank_account (id) ON DELETE CASCADE,
    file_name             TEXT   NOT NULL,
    file_hash             TEXT   NOT NULL,
    period_start          DATE,
    period_end            DATE,
    opening_balance       NUMERIC(19,4),
    closing_balance       NUMERIC(19,4),
    reconciliation_status TEXT CHECK (reconciliation_status IN ('VERIFIED', 'MISMATCH')),
    status                TEXT   NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    imported_count        INT    NOT NULL DEFAULT 0,
    duplicate_count       INT    NOT NULL DEFAULT 0,
    rejected_count        INT    NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- the same file cannot be imported twice, but a FAILED import can be retried
CREATE UNIQUE INDEX ux_bank_statement_file
    ON bank_statement (bank_account_id, file_hash) WHERE status <> 'FAILED';

CREATE TABLE bank_transaction (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bank_account_id        BIGINT NOT NULL REFERENCES bank_account (id) ON DELETE CASCADE,
    statement_id           BIGINT NOT NULL REFERENCES bank_statement (id) ON DELETE CASCADE,
    transaction_date       DATE   NOT NULL,
    original_description   TEXT   NOT NULL,
    normalized_description TEXT   NOT NULL,
    normalizer_version     INT    NOT NULL,
    amount                 NUMERIC(19,4) NOT NULL,
    currency               TEXT   NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    mcc_code               TEXT,
    dedupe_key             TEXT   NOT NULL,
    UNIQUE (bank_account_id, dedupe_key)
);
CREATE INDEX ix_bank_transaction_account_date ON bank_transaction (bank_account_id, transaction_date);
CREATE INDEX ix_bank_transaction_statement ON bank_transaction (statement_id);