-- liquibase formatted sql

-- changeset opencasino:003-users-add-balance splitStatements:false
ALTER TABLE users ADD COLUMN balance DOUBLE PRECISION NOT NULL DEFAULT 0;
-- rollback ALTER TABLE users DROP COLUMN balance;

-- changeset opencasino:003-users-add-display-name splitStatements:false
ALTER TABLE users ADD COLUMN display_name VARCHAR(64);
-- rollback ALTER TABLE users DROP COLUMN display_name;

-- changeset opencasino:003-users-add-first-name splitStatements:false
ALTER TABLE users ADD COLUMN first_name VARCHAR(255);
-- rollback ALTER TABLE users DROP COLUMN first_name;

-- changeset opencasino:003-users-add-last-name splitStatements:false
ALTER TABLE users ADD COLUMN last_name VARCHAR(255);
-- rollback ALTER TABLE users DROP COLUMN last_name;

-- changeset opencasino:003-backfill-from-players dbms:postgresql splitStatements:false
UPDATE users u
SET balance      = p.balance,
    display_name = p.username,
    first_name   = p.first_name,
    last_name    = p.last_name
FROM players p
WHERE p.user_id = u.id;
-- rollback UPDATE users SET balance = 0, display_name = NULL, first_name = NULL, last_name = NULL;

-- changeset opencasino:003-backfill-from-players-h2 dbms:h2 splitStatements:false
UPDATE users SET
    balance      = COALESCE((SELECT p.balance      FROM players p WHERE p.user_id = users.id), balance),
    display_name = COALESCE((SELECT p.username     FROM players p WHERE p.user_id = users.id), display_name),
    first_name   = COALESCE((SELECT p.first_name   FROM players p WHERE p.user_id = users.id), first_name),
    last_name    = COALESCE((SELECT p.last_name    FROM players p WHERE p.user_id = users.id), last_name);
-- rollback UPDATE users SET balance = 0, display_name = NULL, first_name = NULL, last_name = NULL;

-- changeset opencasino:003-display-name-fallback splitStatements:false
UPDATE users SET display_name = SUBSTRING(email, 1, POSITION('@' IN email) - 1) WHERE display_name IS NULL;
-- rollback UPDATE users SET display_name = NULL;

-- changeset opencasino:003-users-display-name-not-null splitStatements:false
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
-- rollback ALTER TABLE users ALTER COLUMN display_name DROP NOT NULL;

-- changeset opencasino:003-drop-players splitStatements:false
DROP TABLE players;
-- rollback CREATE TABLE players (id VARCHAR(64) PRIMARY KEY);
