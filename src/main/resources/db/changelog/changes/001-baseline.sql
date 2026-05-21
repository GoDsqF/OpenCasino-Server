-- liquibase formatted sql

-- changeset opencasino:001-create-users splitStatements:false
CREATE TABLE users (
    id              UUID                     PRIMARY KEY,
    email           VARCHAR(255)             NOT NULL,
    email_verified  BOOLEAN                  NOT NULL DEFAULT TRUE,
    password_hash   VARCHAR(255),
    role            VARCHAR(32)              NOT NULL DEFAULT 'USER',
    balance         DOUBLE PRECISION         NOT NULL DEFAULT 0,
    display_name    VARCHAR(64)              NOT NULL,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT users_email_unique UNIQUE (email)
);
-- rollback DROP TABLE users;

-- changeset opencasino:001-create-user-oauth-identities splitStatements:false
CREATE TABLE user_oauth_identities (
    user_id   UUID         NOT NULL,
    provider  VARCHAR(64)  NOT NULL,
    subject   VARCHAR(255) NOT NULL,
    CONSTRAINT user_oauth_identities_pk PRIMARY KEY (provider, subject),
    CONSTRAINT user_oauth_identities_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
-- rollback DROP TABLE user_oauth_identities;

-- changeset opencasino:001-create-refresh-tokens splitStatements:false
CREATE TABLE refresh_tokens (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL,
    token_hash  CHAR(64)                 NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    user_agent  VARCHAR(512),
    ip          VARCHAR(64),
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash)
);
-- rollback DROP TABLE refresh_tokens;

-- changeset opencasino:001-refresh-tokens-user-id-idx splitStatements:false
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
-- rollback DROP INDEX refresh_tokens_user_id_idx;

-- changeset opencasino:001-refresh-tokens-expires-at-idx splitStatements:false
CREATE INDEX refresh_tokens_expires_at_idx ON refresh_tokens (expires_at);
-- rollback DROP INDEX refresh_tokens_expires_at_idx;

-- changeset opencasino:001-create-balance-ledger splitStatements:false
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

-- changeset opencasino:001-balance-ledger-user-id-idx splitStatements:false
CREATE INDEX balance_ledger_user_id_idx ON balance_ledger (user_id);
-- rollback DROP INDEX balance_ledger_user_id_idx;

-- changeset opencasino:001-balance-ledger-round-id-idx splitStatements:false
CREATE INDEX balance_ledger_round_id_idx ON balance_ledger (round_id);
-- rollback DROP INDEX balance_ledger_round_id_idx;
