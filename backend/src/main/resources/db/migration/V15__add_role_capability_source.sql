-- =============================================================================
-- V15__add_role_capability_source.sql
-- #132: add the ROLE capability source type for role-derived rows.
--
-- Split from the view rewrite (V16): under PG's enum rules a value added with
-- ALTER TYPE ... ADD VALUE cannot be referenced by stored expressions (e.g. a
-- view) created in the same transaction (SQL state 55P04) — and Flyway runs
-- each migration in one transaction. V15 commits the enum value; V16 rewrites
-- active_user_capabilities to use it.
--
-- ROLE rows are emitted ONLY by the view — nothing ever writes source_type
-- 'ROLE' to user_capability (direct grants keep their existing source types).
-- =============================================================================

ALTER TYPE capability_source_type ADD VALUE 'ROLE';
