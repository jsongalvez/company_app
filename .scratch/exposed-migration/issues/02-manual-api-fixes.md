# EM-02 — Manual API fixes (enum, UUID, transaction, custom column)

**What to build:** Fix the non-mechanical API changes that the migration skill can't handle automatically. After this ticket, the full backend should compile without errors and all individual compilation units pass.

**Blocked by:** EM-01

**Status:** ready-for-agent

- [x] **UUID migration** — `Table.uuid()` → `Table.javaUUID()` across 29 model files (39 table definitions). Import `org.jetbrains.exposed.v1.core.java.javaUUID`. All `uuid("column_name").autoGenerate()` calls need updating. Any `import java.util.UUID` in DAO contexts may need `.java` subpackage updates (this project is DSL-only, no DAO, so low risk).
- [x] **Transaction API** — `Transaction` is now abstract. `exec()`, `connection`, `db`, `rollback()` now live on `JdbcTransaction`. Inside `transaction { }` blocks, the receiver is `JdbcTransaction`, so `exec("SELECT 1")` in `HealthRoutes.kt` should still resolve. Verify no custom `Transaction` extensions exist.
- [x] **`transaction()` signature** — parameter order changed: `transaction(db)` is now the canonical form. Check all explicit `transaction(db = ...)` calls.
- [x] **`customEnumeration` PGobject pattern** — 18 definitions across 13 model files. Verify the `toDb`/`fromDb` contract still works in 1.3.1. Test that Postgres native enums (`user_status`, `branch_type`, `day_status`, etc.) read/write correctly. Check if Exposed 1.3.1 has native PG enum support that can replace the PGobject pattern.
- [x] **`JsonBColumnType.readObject()`** — signature changed from `ResultSet` to `RowApi`. Update `AuditLogTable.kt` accordingly (2 column registrations).
- [x] **`ResultRow.get<EnumType>(column)`** — verify explicit type-parameter reads on `customEnumeration` columns still resolve correctly with the new API.
- [x] Compilation passes: `./gradlew :backend:compileKotlin`

**Status:** ✅ done
