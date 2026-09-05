# ADR-0006: Test database isolation and post-test cleanliness check

**Status:** Accepted, amended by #493 (owned schema per test JVM)
**Date:** 2026-07-16, amended 2026-09-05

## Context

Integration tests shared the application database (`company_app`), risking data pollution
between test runs and between test and application usage. Tests cleaned up after themselves via
`BasePostgresTest.cleanTrackedRows()`, but any leak could silently corrupt subsequent test
results. Additionally, leaked test data could accumulate across multiple `./gradlew :backend:test`
runs without any detection mechanism.

## Decision

1. **Dedicated test database.** Tests run against a separate database (`company_app_test`) with
   its own HikariCP connection pool, Flyway migrations, and `Database.connect()` call.
   `DatabaseTestHelper.ensureDatabase()` creates this pool on first call; the main application
   database is never touched by tests.

   The test DB name is configurable via `TEST_DB_NAME` env var (defaults to `${POSTGRES_DB}_test`).
   `docker/init-test-db.sql` auto-creates the test database on container startup.

2. **Post-test cleanliness check.** `scripts/check-test-cleanliness.sh` queries all non-seed
   tables (`pg_tables` minus role, capability, role_capability, flyway_schema_history) and fails
   if any row count > 0. Wired into the quality workflow (`.github/workflows/quality.yml`)
   and runnable locally (`bash scripts/check-test-cleanliness.sh`); git hooks never run it
   (#329/#335 — hooks are bookkeeping only).

## Amendment #493 — owned schema per test JVM

Each backend test JVM creates exactly one `test_w_<pid>_<rand>` schema inside the dedicated
test database, migrates it once via Flyway (`schemas(owned)`), and pins every pooled
connection to `search_path "<owned>", public` at datasource construction
(`currentSchema` + `connectionInitSql` — never a one-off `SET` on one connection).

Javalin handler threads and explicit `Database.connect(requireTestDataSource())` callers
inherit the same path, so every backend connection resolves application objects in the
owned schema. Normal JVM shutdown closes the pool first, then drops only that schema
(`DROP SCHEMA "<owned>" CASCADE` after `requireOwnedSchema` validation); migration
failure closes the pool and drops only the owned schema. Shutdown never scans for or
deletes other schemas — an abruptly killed worker leaves its uniquely named schema
behind and the next run mints a fresh name. The guard fails before DDL when the
resolved test database equals the application database or is not a strict SQL identifier
(blocking JDBC-URL suffix smuggling), fails closed on
blank/`public`/unowned schema names on every path, and refuses to migrate into an
already-existing worker schema (random collision fails loudly instead of sharing tables).

Extensions (`btree_gist`, `pg_trgm`, `pg_stat_statements`) are initialized once in the
stable `public` schema under a `pg_advisory_xact_lock` before the worker migration, and
placement is verified (`pg_extension` must report `public`) rather than silently
relocated — so `V1`/`V3` `CREATE EXTENSION IF NOT EXISTS` stays a no-op inside the
worker migration and dropping a worker schema cannot remove objects another worker
needs. Parallel first-use initialization serializes on the advisory lock.

Methods within one worker stay serial: Exposed's default `Database` singleton and
production singletons already assume that. Isolation is between JVMs only — it does
not make concurrent test methods in one JVM safe.

This is one migration per JVM, not ADR-0004's rejected per-class migration: ADR-0004
rejected Flyway clean+migrate per test class (~2s x hundreds of classes); #493 pays
one migration per `./gradlew :backend:test` worker and keeps the existing row
registry as a temporary bridge until #494 replaces per-row teardown with schema
reset. `WorkerSchemaLifecycleTest` owns the lifecycle proof (search_path order,
`current_schema()`, extension placement + `similarity()` usability, migration/view/
trigger presence, enum-backed writes, two-schema disjointness, public-sentinel
survival across init and disposal, guard rejections).

Backend-suite evidence is that lifecycle test plus the full `:backend:test` run —
`scripts/check-test-cleanliness.sh` no longer proves anything about backend tests
because backend workers never touch `public` tables. The script, `clean-test-db.sh`,
and `scripts/lib/common.sh` stay for k6 load-test cleanup on the `public` test
database; quality/local-ci no longer run the public check as a backend gate.

## Consequences

- Tests cannot corrupt or pollute the main application database: the guard refuses the
  application database name before DDL, and workers only ever drop their own validated
  `test_w_*` schema (CI names the app DB `company_app` precisely so the guard is live).
- Leaked backend test data cannot accumulate across `./gradlew :backend:test` runs — each
  worker mints a fresh owned schema and drops it on normal JVM shutdown; a killed worker's
  uniquely named leftover never blocks the next run (no scanning/deleting others).
- `DatabaseTestHelper.testDataSource` provides the HikariDataSource for remaining raw JDBC
  helpers (trigger DDL, legacy product-sale inserts in RemittanceService and
  MonthlyRemittanceSummary tests). Raw JDBC is confined to test code only.
- Seed data (roles, capabilities) is migrated per worker schema by Flyway (V2) — no shared
  seed rows across workers; the `public` cleanliness scripts stay for k6 only.
- Adding a new table to the schema requires no test-infrastructure changes; per-worker Flyway
  migrates it automatically. The temporary row registry (`BasePostgresTest.trackOwned`) stays
  until #494 replaces it with schema reset.
