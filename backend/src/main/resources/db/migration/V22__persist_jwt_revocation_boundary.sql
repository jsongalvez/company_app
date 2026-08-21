-- Keep JWT revocation independent from reversible user deactivation state.
ALTER TABLE app_user ADD COLUMN jwt_revoked_at TIMESTAMPTZ;

UPDATE app_user
SET jwt_revoked_at = COALESCE(deactivated_at, CURRENT_TIMESTAMP)
WHERE status = 'INACTIVE';
