-- liquibase formatted sql

-- TEMPORARY: pre-launch giveaway — new accounts start with 50000 instead of 0.
-- Remove this file (and its DATABASECHANGELOG row) once the launch promo ends
-- and revert the default back to 0 via a follow-up migration.

-- changeset opencasino:999-temp-users-balance-default-50000 splitStatements:false
ALTER TABLE users ALTER COLUMN balance SET DEFAULT 50000;
-- rollback ALTER TABLE users ALTER COLUMN balance SET DEFAULT 0;
