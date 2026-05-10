-- liquibase formatted sql

-- changeset opencasino:001-baseline-players splitStatements:false
-- preconditions onFail:MARK_RAN onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'players'
CREATE TABLE players (
    id              VARCHAR(64)      PRIMARY KEY,
    username        VARCHAR(255)     NOT NULL,
    balance         DOUBLE PRECISION NOT NULL DEFAULT 0,
    first_name      VARCHAR(255)     NOT NULL,
    last_name       VARCHAR(255)     NOT NULL,
    email           VARCHAR(255),
    user_hash       VARCHAR(255),
    created_at      BIGINT           NOT NULL,
    last_modified   BIGINT           NOT NULL
);
-- rollback DROP TABLE players;
