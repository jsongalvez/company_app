# 01 — Merge V3 migration into V1

**What to build:** Move the `middle_name` column and `idx_client_trgm` trigram index from `V3__client_middle_name_trgm.sql` into `V1__full_schema.sql`, then delete the V3 file. The schema lives in one migration.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `middle_name TEXT` column added to `client` table in V1 (after `last_name`, before `suffix`)
- [ ] `CREATE INDEX idx_client_trgm ON client USING gin (first_name gin_trgm_ops, middle_name gin_trgm_ops, last_name gin_trgm_ops)` appended to V1
- [ ] `V3__client_middle_name_trgm.sql` file deleted
- [ ] `./gradlew :backend:test` passes (V1 checksum change handled by existing `flyway.repair()` in `DatabaseConfig`)
- [ ] `./gradlew :backend:detekt :backend:ktlintCheck` passes
