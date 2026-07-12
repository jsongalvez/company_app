# Backend (Kotlin + Javalin + Exposed)

Package root: `com.companyb.companyapp`. Layers: `api/routes`, `service`, `repository`
(+ `repository/model` for Exposed `Table` objects), `auth`, `database`, `logging`.

## Quality gate (run before every commit)

The pre-commit hook (`.githooks/pre-commit`) enforces these gates automatically:

1. **Static analysis & tests:** `./gradlew :backend:detekt :backend:ktlintCheck :backend:test`
2. **App boot verification:** Postgres must be reachable, then the app is started and confirmed listening on its port before the commit is allowed.

Install hooks once: `bash scripts/setup-hooks.sh` (sets `core.hooksPath = .githooks`).

To run manually: `./gradlew :backend:detekt :backend:ktlintCheck :backend:test`

Auto-fix formatting: `./gradlew :backend:ktlintFormat`.

Run the app: `./gradlew :backend:run` (requires Postgres at `DB_HOST:DB_PORT`).

- **detekt `MagicNumber` is strict.** Extract every literal (column widths, pool sizes,
  durations, lengths) into a `private const val`; constants are exempt. `ReturnCount` max is 2 —
  use `@Suppress("ReturnCount")` to match the existing pattern in `AuthService` when a guard-clause
  style is clearer.
- detekt needs the `detekt-formatting` plugin (wired in the root `build.gradle.kts`
  `subprojects { dependencies { add("detektPlugins", ...) } }`).

## Routes & authentication

Register **public** routes (login/register) directly (e.g. `/auth/login`). Register **protected**
routes under `/api/*` so the `config.routes.before("/api/*")` filter authenticates the JWT and sets
the caller's id via `context.attribute("userId", ...)` (a `String` JWT subject). Read it back with
`UUID.fromString(context.attribute<String>("userId"))`. Wire each route object into
`Main.initializeJavalin` alongside `AuthRoutes`.

## Authorization

All operational permission checks MUST go through
`CapabilityService.hasCapability(userId, capabilityCode, contextType, contextId)`, which queries the
`active_user_capabilities` SQL view (defined in `V1__full_schema.sql`). **Never check roles directly
in business logic** — roles only seed capabilities in the V2 migration. The view already excludes
INACTIVE users and out-of-window grants.

GLOBAL-scoped capabilities (`MANAGE_USERS`, `ASSIGN_DELEGATE`) have no specific branch/day; pass
`CapabilityContextType.GLOBAL` with `contextId = CapabilityService.GLOBAL_CONTEXT_ID` (the nil
all-zero UUID).

When inserting `user_capability` rows (e.g. for relief access grants or delegate assignments), use
`CapabilityRepository.findIdByCode("EDIT_BRANCH_DATA")` to look up the capability ID, then use the
Exposed DSL `UserCapabilityTable.insert {}` with `customEnumeration` columns (see below).

## Audit logging

Every mutating service must write an audit row via `AuditLogRepository.record(tableName, recordId,
action, changedBy, oldValue?, newValue?, reason?)`. `record` opens its own `transaction {}` which
**joins an enclosing transaction** (Exposed reuses the connection unless nested transactions are
explicitly enabled), so the audit insert commits atomically with the change it describes — call it
inside the same repository `transaction {}` that performs the mutation. Build JSON values with
`AuditLogRepository.jsonField(key, value)` (safely escaped).

Immediate revocation uses the in-memory `DenyList` (`ConcurrentHashMap<UUID, Instant>`), checked
inside `JwtService.verifyToken` BEFORE any DB lookup, populated at startup
(`Main.initializeDenyList`), and auto-evicting entries older than 24h (JWT max expiry).

## HTTP errors & day state

Signal HTTP errors by throwing Javalin's built-in response exceptions from the service layer:
`ForbiddenResponse` (403), `BadRequestResponse` (400), `NotFoundResponse` (404),
`UnauthorizedResponse` (401). There is no custom exception hierarchy.

Before any operational/financial write, resolve the owning day with
`BranchDayService.resolveOrCreate(branchId, date)` and gate it with
`BranchDayService.assertEditable(branchDayId, userId, reason?)`. Day state is **lazy**: an OPEN day
whose calendar date precedes today (Asia/Manila) is treated as PAST without a DB write — reuse
`BranchDayService.evaluateStatus` instead of re-deriving. `EDIT_PAST_DAY` is scoped to `BRANCH`
context (contextId = `branch_day.branch_id`).

## Database access — Exposed DSL only

**All database access MUST use the Exposed DSL.** Raw SQL (`exec()`, `TransactionManager.current().exec()`, `TextColumnType` binds) is forbidden in repositories. The codebase was migrated away from raw SQL in favor of type-safe DSL queries.

### Table & view definitions

- Every table and view needs an Exposed `Table` / `object` in `repository/model/`.
- Views (e.g. `ActiveUserCapabilitiesView`) are modeled as `Table` objects with the view name; they are read-only — never insert/update/delete against them.
- Add `exposed-java-time` for `timestampWithTimeZone` / `CurrentTimestampWithTimeZone`.

### PostgreSQL native enums

Postgres enums (`user_status`, `branch_type`, `day_status`, `capability_context_type`,
`capability_source_type`, `audit_action`) must use `customEnumeration` with a `PGobject` binding
so the JDBC driver sends the correct PG type:

```kotlin
val status = customEnumeration<UserStatus>(
    name = "status",
    sql = "user_status",
    fromDb = { value -> UserStatus.valueOf(value as String) },
    toDb = {
        val obj = PGobject()
        obj.type = "user_status"
        obj.value = it.name
        obj
    },
).default(UserStatus.ACTIVE)
```

Never use `enumerationByName` — it binds values as `varchar` and will fail with
`operator does not exist` at runtime.

### Idempotent inserts (ON CONFLICT DO NOTHING)

Use `insertIgnore` and check `insertedCount` to detect whether the row was newly created:

```kotlin
val wasInserted = SomeTable.insertIgnore {
    it[id] = id
    it[name] = name
}.insertedCount > 0
val row = SomeTable.selectAll().where { SomeTable.id eq id }.single()
```

### Query patterns

| Purpose | DSL |
|---|---|
| Select by id | `Table.selectAll().where { Table.id eq id }.singleOrNull()` |
| Select with filters | `Table.selectAll().where { (col eq val) and (col2 eq val2) }` |
| Ordered select | `.selectAll().orderBy(Table.col to SortOrder.ASC)` |
| Existence check | `.selectAll().where { ... }.empty().not()` |
| Insert | `Table.insert { it[col] = value }` |
| Update | `Table.update({ whereClause }) { it[col] = value }` |
| Join | `TableA.innerJoin(TableB, { fk }, { pk }).selectAll().where { ... }` |

### Anti-patterns — DO NOT

- ❌ Raw SQL: `exec(sql, args = listOf(TextColumnType() to value)) { rs -> ... }`
- ❌ `TransactionManager.current().exec(...)`
- ❌ Manual `ResultSet` row mapping with `getString()`/`getObject()`
- ❌ `enumerationByName` for Postgres enum columns
- ❌ SQL casts in strings (`?::uuid`, `?::jsonb`, `?::branch_type`)

### JSONB columns

`AuditLogTable.oldValue` / `newValue` use a custom `JsonBColumnType` (defined in `AuditLogTable.kt`)
that binds via `PGobject(type = "jsonb")`. New JSONB columns should reuse this type:

```kotlin
val myJsonCol = registerColumn("my_json_col", JsonBColumnType()).nullable()
```

## Clock-in / Attendance

Clock-in (`POST /api/attendance/clock-in`) is open to all authenticated users (no capability gate).
The service determines `is_relief` by checking for an active `user_branch_assignment` at the target
branch: if no assignment exists, the user clocks in as relief.

Before inserting, check for an existing active clock-in via `AttendanceRepository.hasActiveClockIn`
and throw `io.javalin.http.ConflictResponse` (409) if found — this prevents the unique index
violation on `idx_one_active_clock_in`.

The attendance insert and `branch_day_assignment` upsert happen in a single transaction using
`insertIgnore` for idempotency. `insertIgnore` with `insertedCount` detects whether the row was
newly inserted; if the row already exists (count = 0), the existing row is read back with a
follow-up `selectAll`.

Clock-out (`POST /api/attendance/clock-out`) is a non-mutating lookup for idempotency: if the
attendance record already has `clock_out` set, return the existing row with HTTP 200. Otherwise,
update `clock_out = now()` where `clock_out IS NULL` (using `Table.update` with `and` condition),
write an audit log entry for the UPDATE, and return the updated record. `isRelief` is fetched from
`branch_day_assignment` via `AttendanceRepository.branchDayAssignmentIsRelief`.

## Testing

Integration tests use a real Postgres instance via `DatabaseTestHelper.ensureDatabase()` (connects
via the root `.env` configuration). Run all tests with `./gradlew :backend:test`. Prefer DB-free
unit tests for pure-logic helpers; inject time through internal `*At(now: Instant)` helpers (see
`DenyListTest`).

Gradle backend tests run from the repo root (`tasks.test.workingDir = rootProject.projectDir`) so
dotenv-kotlin can load the root `.env`.
