-- Auth hardening: account deactivation, refresh-token revocation, password reset.
-- Run via `npm run migrate` (idempotent) or paste into phpMyAdmin's SQL tab.
--
--   is_active            0 blocks login and token refresh (admin deactivation)
--   token_version        embedded in refresh JWTs; bumping it invalidates all
--                        outstanding refresh tokens (logout, password change)
--   reset_token_hash     SHA-256 hex of the single-use password reset token —
--                        only the hash is stored, so a DB leak can't be used
--                        to take over accounts
--   reset_token_expires  reset link validity cutoff (1 hour from request)

ALTER TABLE users ADD COLUMN is_active           TINYINT(1)   NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN token_version       INT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN reset_token_hash    VARCHAR(64)  NULL;
ALTER TABLE users ADD COLUMN reset_token_expires DATETIME     NULL;
