CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    username VARCHAR NOT NULL,
    balance NUMERIC(15,2),
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    email VARCHAR NOT NULL,
    user_hash VARCHAR,
    created_at TIMESTAMP,
    last_modified TIMESTAMP,
    CONSTRAINT unique_user UNIQUE (username, email)
);