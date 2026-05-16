-- liquibase formatted sql

-- Phase 6 simplifies onboarding: there is no email-verification flow yet, so
-- every account (local + OAuth) is trusted to own its email at create time.
-- This unblocks OAuth-to-local linking, which would otherwise refuse every
-- existing local user (all created with email_verified=false in Phase 2).

-- changeset opencasino:004-users-email-verified-default-true splitStatements:false
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT TRUE;
-- rollback ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;

-- changeset opencasino:004-backfill-email-verified splitStatements:false
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
-- rollback UPDATE users SET email_verified = FALSE;
