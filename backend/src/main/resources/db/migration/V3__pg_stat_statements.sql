-- =============================================================================
-- V3__pg_stat_statements.sql — slow-query visibility (#474)
--
-- Installs pg_stat_statements so top-slow statements (normalized query text,
-- mean time, call count) are queryable via the public.pg_stat_statements view.
-- The extension creates the view with zero rows; accumulation starts only
-- once the server preloads the library (shared_preload_libraries, postmaster
-- context, needs a restart) — see docker/docker-compose.yml (local dev),
-- .github/workflows/quality.yml (CI test DB), and docs/slow-query-runbook.md
-- (Coolify Postgres). CREATE EXTENSION itself needs no preload and is safe on
-- every database, so this migration never fails startup.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
