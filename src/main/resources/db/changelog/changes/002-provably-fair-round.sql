-- liquibase formatted sql

-- changeset opencasino:002-create-provably-fair-round splitStatements:false
CREATE TABLE provably_fair_round (
    id                UUID                     PRIMARY KEY,
    round_id          UUID                     NOT NULL,
    game_type         VARCHAR(32)              NOT NULL,
    server_seed_hash  CHAR(64)                 NOT NULL,
    revealed_seed     VARCHAR(128),
    client_seed       VARCHAR(255)             NOT NULL,
    outcome           TEXT                     NOT NULL,
    house_edge        DOUBLE PRECISION,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
-- rollback DROP TABLE provably_fair_round;

-- changeset opencasino:002-provably-fair-round-round-id-idx splitStatements:false
CREATE INDEX provably_fair_round_round_id_idx ON provably_fair_round (round_id);
-- rollback DROP INDEX provably_fair_round_round_id_idx;
