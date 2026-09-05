-- =============================================================================
-- V4__credential_version.sql — bind JWTs to the credential generation verified (#505)
--
-- Adds app_user.credential_version so token issuance can prove password verification
-- happened after the latest reset/invite-accept instead of relying on wall-clock
-- ordering alone. Incremented on every password-hash replacement; embedded as the
-- `cred_ver` JWT claim and checked for equality in UserRepository.authorize.
-- Logout/deactivation keep the persisted jwt_revoked_at boundary; version covers
-- the reset race where a stale hash read overlaps a completed reset.
-- =============================================================================

ALTER TABLE app_user ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
