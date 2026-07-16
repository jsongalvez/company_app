# ADR-0006: Test database isolation and post-test cleanliness check

**Status:** Accepted
**Date:** 2026-07-16

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
   if any row count > 0. Wired into:
   - `pre-commit` hook (step 2b, runs after quality gate tests complete)
   - `pre-push` hook (step 1, runs before JMH benchmarks)

## Consequences

- Tests cannot corrupt or pollute the main application database.
- Leaked test data is detected immediately at commit/push time, not silently accumulated.
- `DatabaseTestHelper.testDataSource` provides the HikariDataSource for remaining raw JDBC
  helpers (trigger DDL, legacy product-sale inserts in RemittanceService and
  MonthlyRemittanceSummary tests). Raw JDBC is confined to test code only.
- Seed data (roles, capabilities) is seeded by Flyway and shared across test runs — the
  cleanliness check explicitly excludes these tables.
- Adding a new table to the schema requires no changes to the cleanliness check; only the
  seed-table exclusion list needs updating if the new table is a seed table.
