-- liquibase formatted sql

-- changeset opencasino:005-create-refresh-tokens splitStatements:false
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    user_agent  VARCHAR(512),
    ip          VARCHAR(64),
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash)
);
-- rollback DROP TABLE refresh_tokens;

-- changeset opencasino:005-refresh-tokens-user-id-idx splitStatements:false
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
-- rollback DROP INDEX refresh_tokens_user_id_idx;

-- changeset opencasino:005-refresh-tokens-expires-at-idx splitStatements:false
CREATE INDEX refresh_tokens_expires_at_idx ON refresh_tokens (expires_at);
-- rollback DROP INDEX refresh_tokens_expires_at_idx;