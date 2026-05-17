-- liquibase formatted sql

-- changeset opencasino:006-create-balance-ledger splitStatements:false
CREATE TABLE balance_ledger (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL,
    round_id    UUID                     NOT NULL,
    delta       DOUBLE PRECISION         NOT NULL,
    reason      VARCHAR(32)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT balance_ledger_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
-- rollback DROP TABLE balance_ledger;

-- changeset opencasino:006-balance-ledger-user-id-idx splitStatements:false
CREATE INDEX balance_ledger_user_id_idx ON balance_ledger (user_id);
-- rollback DROP INDEX balance_ledger_user_id_idx;

-- changeset opencasino:006-balance-ledger-round-id-idx splitStatements:false
CREATE INDEX balance_ledger_round_id_idx ON balance_ledger (round_id);
-- rollback DROP INDEX balance_ledger_round_id_idx;