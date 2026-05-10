-- liquibase formatted sql

-- changeset opencasino:002-create-users splitStatements:false
CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(255),
    role            VARCHAR(32)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT users_email_unique UNIQUE (email)
);
-- rollback DROP TABLE users;

-- changeset opencasino:002-create-user-oauth-identities splitStatements:false
CREATE TABLE user_oauth_identities (
    user_id   UUID         NOT NULL,
    provider  VARCHAR(64)  NOT NULL,
    subject   VARCHAR(255) NOT NULL,
    CONSTRAINT user_oauth_identities_pk PRIMARY KEY (provider, subject),
    CONSTRAINT user_oauth_identities_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
-- rollback DROP TABLE user_oauth_identities;

-- changeset opencasino:002-players-add-user-id splitStatements:false
ALTER TABLE players ADD COLUMN user_id UUID;
-- rollback ALTER TABLE players DROP COLUMN user_id;

-- changeset opencasino:002-players-backfill-users dbms:postgresql splitStatements:false
INSERT INTO users (id, email, email_verified, password_hash, role, created_at, updated_at)
SELECT gen_random_uuid(),
       COALESCE(p.email, p.id || '@legacy.local'),
       FALSE,
       NULL,
       'USER',
       to_timestamp(p.created_at),
       to_timestamp(p.last_modified)
FROM players p
WHERE p.user_id IS NULL
ON CONFLICT (email) DO NOTHING;

UPDATE players p
SET user_id = u.id
FROM users u
WHERE p.user_id IS NULL
  AND u.email = COALESCE(p.email, p.id || '@legacy.local');
-- rollback DELETE FROM users WHERE email LIKE '%@legacy.local';

-- changeset opencasino:002-players-backfill-users-h2 dbms:h2 splitStatements:false
INSERT INTO users (id, email, email_verified, password_hash, role, created_at, updated_at)
SELECT RANDOM_UUID(),
       COALESCE(p.email, p.id || '@legacy.local'),
       FALSE,
       NULL,
       'USER',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM players p
WHERE p.user_id IS NULL;

UPDATE players SET user_id = (
    SELECT u.id FROM users u WHERE u.email = COALESCE(players.email, players.id || '@legacy.local')
) WHERE user_id IS NULL;
-- rollback DELETE FROM users WHERE email LIKE '%@legacy.local';

-- changeset opencasino:002-players-user-id-not-null splitStatements:false
ALTER TABLE players ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE players ADD CONSTRAINT players_user_fk FOREIGN KEY (user_id) REFERENCES users (id);
-- rollback ALTER TABLE players DROP CONSTRAINT players_user_fk;
-- rollback ALTER TABLE players ALTER COLUMN user_id DROP NOT NULL;

-- changeset opencasino:002-players-drop-user-hash splitStatements:false
ALTER TABLE players DROP COLUMN user_hash;
-- rollback ALTER TABLE players ADD COLUMN user_hash VARCHAR(255);
