CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    username VARCHAR NOT NULL,
    balance NUMERIC(11,2)
    email VARCHAR,
    user_hash VARCHAR,
    created_at
    last_modified
    CONSTRAINT unique_user UNIQUE (username, email)
);