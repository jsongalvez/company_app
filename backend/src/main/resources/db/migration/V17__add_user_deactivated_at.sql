-- =============================================================================
-- V17__add_user_deactivated_at.sql
-- #133: deactivated_at timestamp on app_user (powers the User Management
-- screen's "deactivated X ago" display). Set on deactivate (status flip to
-- INACTIVE), cleared on reactivate (status flip back to ACTIVE).
-- =============================================================================

ALTER TABLE app_user ADD COLUMN deactivated_at TIMESTAMPTZ;
