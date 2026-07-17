# Backend (Kotlin + Javalin + Exposed)

## Core Documents

Before coding, read the relevant doc(s) in `docs/`:

| File | Use when |
|------|----------|
| `docs/architecture_implementation_plan.md` | You need architecture decisions, module map, or implementation sequence |
| `docs/engine_specifications.md` | You need exact pseudocode or SQL for the commission, delegate, or remittance engines |
| `docs/business_requirements.md` | You need domain terminology, business rules, or constraints |
| `docs/design_specification.md` | You need the tech stack, repo structure, or deployment context |

## Schema

The authoritative schema is `backend/src/main/resources/db/migration/V1__full_schema.sql`.

Package root: `com.companyb.companyapp`. Layers: `api/routes`, `service`, `repository`
(+ `repository/model` for Exposed `Table` objects), `auth`, `database`, `logging`.

## Quality gate (run before every commit)

The pre-commit hook (`.githooks/pre-commit`) enforces these gates automatically:

1. **Formatting:** ktlint scoped to staged `.kt`/`.kts` files via `ktlint --format` CLI (falls back to project-wide `./gradlew ktlintFormat` if CLI not on PATH)
2. **Static analysis, tests & JMH benchmarks:** `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :backend:jmh`
3. **App boot verification:** Postgres must be reachable, then the app is started and confirmed listening on its port before the commit is allowed.

A pre-push hook (`.githooks/pre-push`) additionally runs the full JMH benchmark suite and
reminds you to compare against `backend/jmh-baselines.md` with `bash scripts/check-baselines.sh`.

Install hooks once: `bash scripts/setup-hooks.sh` (sets `core.hooksPath = .githooks`).

To run manually: `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :backend:jmh` and then `bash scripts/check-baselines.sh`

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
`context.callerUuid()` (defined in `RoutesUtil.kt` — uses `runCatching { UUID.fromString(...) }`
with a null guard). Wire each route object into
`Main.initializeJavalin` alongside `AuthRoutes`.

### Query parameter extraction

Always use explicit null-check + throw, not `!!` inside `runCatching`:

```kotlin
// DO
val param = context.queryParam("key") ?: throw BadRequestResponse("key is required")
val id = runCatching { UUID.fromString(param) }.getOrElse { throw BadRequestResponse("Invalid key") }

// DON'T
val id = runCatching { UUID.fromString(context.queryParam("key")!!) }
    .getOrElse { throw BadRequestResponse("Invalid or missing key") }
```

### Parent-child URL scoping (CRITICAL)

When a REST URL embeds a parent resource ID (e.g.
`/api/remittances/{remittanceId}/lines/{lineId}`), the service **must** verify the child belongs
to that parent. Never resolve the parent only for existence/status checks while the child lookup
operates on a different parent:

```kotlin
// WRONG — line can belong to remittance A while operating through remittance B's URL
fun removeLine(callerId: UUID, remittanceId: UUID, lineId: UUID): Line {
    val remittance = RemittanceRepository.findById(remittanceId) ?: throw NotFoundResponse(...)
    // ... check remittance status ...
    return RemittanceLineRepository.softDelete(lineId, callerId, remittance.version)
}

// CORRECT — pass parentId to repository and include it in the WHERE clause
fun removeLine(callerId: UUID, remittanceId: UUID, lineId: UUID): Line {
    val remittance = RemittanceRepository.findById(remittanceId) ?: throw NotFoundResponse(...)
    // ... check remittance status ...
    return RemittanceLineRepository.softDelete(lineId, remittanceId, callerId, remittance.version)
}
```

In the repository, scope the lookup to the parent:

```kotlin
// WRONG
val existing = Table.selectAll().where { Table.id eq lineId }.singleOrNull()

// CORRECT
val existing = Table.selectAll().where {
    (Table.id eq lineId) and (Table.parentId eq parentId)
}.singleOrNull()
```

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

**Read endpoints must also gate on capabilities.** If a write endpoint (POST/PATCH/DELETE) checks a
capability, the corresponding read endpoint (GET) should check the same capability. Example:
`ExpenseService.create` and `ExpenseService.softDelete` gate on `EDIT_BRANCH_DATA`, so
`ExpenseService.findByBranchDayId` must also check `EDIT_BRANCH_DATA`.

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

Note: `Transaction` is now abstract in Exposed 1.3.1. Inside `transaction {}` blocks, the receiver is `JdbcTransaction`. Methods like `exec()`, `connection`, `db`, and `rollback()` live on `JdbcTransaction`.

### Import conventions (Exposed 1.3.1)

All imports use the `org.jetbrains.exposed.v1.*` package prefix:
- Core types & top-level functions: `org.jetbrains.exposed.v1.core.*`
- JDBC-specific (transaction, selectAll, insert, etc.): `org.jetbrains.exposed.v1.jdbc.*`
- Java-time column types: `org.jetbrains.exposed.v1.javatime.*`
- ForUpdateOption: `org.jetbrains.exposed.v1.core.vendors.ForUpdateOption`

Top-level operator functions (`eq`, `and`, `or`, `isNull`, `greaterEq`, etc.) are in
`org.jetbrains.exposed.v1.core.*`. They **must be imported explicitly** — they are no longer
available via `SqlExpressionBuilder` receiver in `where {}` blocks.

### Table & view definitions

- Every table and view needs an Exposed `Table` / `object` in `repository/model/`.
- Views (e.g. `ActiveUserCapabilitiesView`) are modeled as `Table` objects with the view name; they are read-only — never insert/update/delete against them.
- Add `exposed-java-time` for `timestampWithTimeZone` / `CurrentTimestampWithTimeZone`.
- Use `javaUUID()` (not `uuid()`) for `java.util.UUID` columns. Import `org.jetbrains.exposed.v1.core.java.javaUUID`.

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

⚠️ `defaultExpression(CurrentTimestampWithTimeZone)` does NOT work with `insertIgnore` — the
expression is not emitted. Columns with a DEFAULT expression must be explicitly set in the
`insertIgnore` block (e.g., `it[effectiveFrom] = OffsetDateTime.now(ZoneOffset.UTC)`).

### Timestamp consistency

Always use `org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone` (the DB server's
clock) when writing timestamp values inside `transaction {}` blocks. Never use
`java.time.OffsetDateTime.now()` or `java.time.LocalDateTime.now()` — JVM clock and DB clock may
diverge (timezone, drift). The only exception is `insertIgnore` blocks where the default expression
is suppressed and you must supply a value manually (use `OffsetDateTime.now(ZoneOffset.UTC)`).

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
- ❌ Using `uuid()` for `java.util.UUID` columns (use `javaUUID()` instead)

### JSONB columns

`AuditLogTable.oldValue` / `newValue` use a custom `JsonBColumnType` (defined in `AuditLogTable.kt`)
that binds via `PGobject(type = "jsonb")`. New JSONB columns should reuse this type:

```kotlin
val myJsonCol = registerColumn("my_json_col", JsonBColumnType()).nullable()
```

## Sessions

Session create (`POST /api/sessions`) uses an idempotent PK lookup first (`SessionRepository.findById`) to handle retries with the same UUID before checking the PENDING guard (`hasActivePendingSession`). This prevents `ConflictResponse` for idempotent retries.

The `computeSessionType` pure function is extracted from the service so it can be unit-tested without a database. The prior session count excludes MEDICAL_MISSION sessions and voided sessions (via `active_session_voids` view LEFT JOIN).

For concurrency, the partial unique index `idx_client_one_pending_session` is the database-level backstop against duplicate PENDING sessions for the same client — the service pre-check (`hasActivePendingSession`) is the first line of defense, followed by the unique index. Exposed `Query.forUpdate()` is not available; rely on unique indexes + pre-checks.

Session status update (`PATCH /api/sessions/{sessionId}/status`) uses Exposed DSL `SessionTable.update({ (id eq sessionId) and (version eq expectedVersion) })` for atomic optimistic locking — if the version doesn't match, no rows are updated and the service throws 409 Conflict. The version is incremented by setting `it[SessionTable.version] = expectedVersion + 1`. Call `AuditLogRepository.record` inside the same `transaction {}` block. The DB has `CONSTRAINT walk_in_status CHECK (NOT (is_walk_in = true AND session_status IN ('NO_SHOW', 'CANCELLED')))` — always validate this at the service layer for a cleaner 400 error before hitting the DB constraint.

Session practitioner management (`POST/PATCH/DELETE /api/sessions/{sessionId}/practitioners`) uses the `session_practitioner` table (UNIQUE on session_id + practitioner_id) with `insertIgnore` for idempotent adds. When adding a practitioner, `slot_at_time` is snapshotted from the user's active `user_branch_assignment` at the session's branch (defaults to 999 if no assignment exists). Each practitioner mutation (add, update remarks, remove) atomically increments `session.version` using a read-then-write pattern (`select version then update to version + 1`). All mutations gate on `EDIT_BRANCH_DATA` capability and call `BranchDayService.assertEditable`.

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

## Performance & benchmarking

Adding a feature that touches a hot path? You **must** verify it didn't regress performance.

### Quick regression check — `measureTimedValue`

Drop timing assertions into existing `*PostgresTest.kt` or create a `*PerformanceTest.kt` in the
same test source set. No extra dependencies:

```kotlin
import kotlin.time.measureTimedValue
import kotlin.time.Duration.Companion.milliseconds

val (result, duration) = measureTimedValue { service.computeSomething(input) }
assertTrue(
    duration < 500.milliseconds,
    "Performance regression: computeSomething took $duration, expected < 500ms"
)
```

Use generous thresholds initially; tighten them after a few runs establish a baseline.

### Hot paths to guard

These are the most performance-sensitive call paths — always add a timing assertion when
modifying code in these areas:

1. **`computeSessionType`** (`SessionTypeAlgorithm`) — pure function, fast, unit-testable
2. **`CommissionService`** calculations — run per-session, high call volume
3. **`BranchDayService.resolveOrCreate` / `evaluateStatus`** — called on every write
4. **`RemittanceService`** — aggregation queries over large line sets
5. **`HasCapability`** / `active_user_capabilities` view — checked on every API call

### JMH microbenchmarks

JMH is wired into the backend module via the `me.champeau.jmh` Gradle plugin. Benchmarks live
under `backend/src/jmh/java/` (Java source set — JMH annotation processing is Java-only).

Run all benchmarks:
```bash
./gradlew :backend:jmh
```

**Writing a new benchmark** — add a Java file under
`backend/src/jmh/java/com/companyb/companyapp/benchmark/`. Kotlin `object` singletons and
top-level functions are accessible from Java as `ClassName.INSTANCE` or `ClassNameKt`:

```java
package com.companyb.companyapp.benchmark;

import com.companyb.companyapp.service.SomeService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class SomeBenchmark {

    @Benchmark
    public void myHotPath(Blackhole bh) {
        bh.consume(SomeService.INSTANCE.someMethod(input));
    }
}
```

The Gradle config (warmup, iterations, fork, threads) is in `backend/build.gradle.kts` under
the `jmh { }` block. Override per-benchmark with `@Warmup` / `@Measurement` annotations.

### JFR (JDK Flight Recorder) — zero-instrumentation profiling

No extra dependencies — JFR is built into the JVM (>= 11). Two Gradle tasks are wired in
`backend/build.gradle.kts`:

```bash
# Standard recording (CPU, lock contention, I/O)
./gradlew :backend:runWithJfr

# Allocation-profiling recording (also captures object allocations)
./gradlew :backend:runWithJfrAllocation
```

Both write the recording to `logs/recording.jfr` / `logs/recording-alloc.jfr`.

Analyze recordings with:
- **JDK Mission Control** (`jmc`) — install separately (OpenJDK or Azul builds)
- **`jfr view`** — built into JDK 21+: `jfr view logs/recording.jfr`
- **Async Profiler converter**: `java -jar converter.jar jfr2flame recording.jfr flamegraph.html`

Key things to look for in JFR:
- **Hot Methods** tab → CPU-bound methods (sort by self time)
- **Allocations** tab (only with `allocation` profile) → object creation hotspots
- **Java Monitor Blocked** events → lock contention
- **Socket I/O** events → database/network bottlenecks

### HTTP-level load testing (k6)

A baseline k6 script lives at `scripts/load-test/baseline.js`. It hits the public and
authenticated API endpoints in a staged ramp-up pattern.

Prerequisite: install k6 (`brew install k6`, `apt install k6`, or download from k6.io).

```bash
# Against local dev server (requires a registered user in the DB)
TEST_USERNAME=owner TEST_PASSWORD=pass \
  k6 run scripts/load-test/baseline.js

# Against a remote server
API_BASE_URL=https://api.example.com TEST_USERNAME=owner TEST_PASSWORD=pass \
  k6 run scripts/load-test/baseline.js
```

The baseline enforces these thresholds (edit `options.thresholds` in the script to adjust):
- `branches_latency`: p95 < 500ms
- `clients_search_latency`: p95 < 1000ms
- `sessions_latency`: p95 < 1000ms
- `errors`: rate < 5%

**Adding a new endpoint to the baseline** — edit `scripts/load-test/baseline.js`:
1. Add a `Trend` metric for the endpoint's latency
2. Add a threshold in `options.thresholds`
3. Add the `http.get`/`http.post` call in the `default` function
4. Run `k6 run` to establish a baseline p95, then tighten the threshold

Remember: k6 tests the full HTTP stack — serialization, Javalin routing, JDBC, connection
pooling, and auth middleware. Regressions here won't show up in JMH benchmarks.

A comprehensive k6 test suite also lives at `tests/k6/`. It covers all critical API paths
with staged ramp-up, concurrency edge cases (409, idempotency), authz edge cases (401, 403),
and concurrent remittance submission testing:

```bash
# Full load test (all endpoints, 5 VUs ramp-up)
TEST_USERNAME=owner TEST_PASSWORD=pass k6 run tests/k6/full-suite.js

# Concurrency edge cases (pending guard, version mismatch, idempotency)
k6 run tests/k6/concurrency-test.js

# Authz edge cases (invalid token, expired token, insufficient capability)
LIMITED_USERNAME=limited LIMITED_PASSWORD=pass k6 run tests/k6/authz-test.js

# Concurrent remittance submission (serializable isolation race)
k6 run tests/k6/remittance-race-test.js
```

### Threshold tuning — when and how to adjust limits

All tools (JMH, measureTimedValue, k6) have hardcoded thresholds. **Do not blindly raise a
threshold to make a failing test pass.** Follow this decision tree:

```
Threshold violation detected
│
├─ JMH scores for the same hot path dropped >20%?
│   → REAL REGRESSION. Fix the code, don't raise the threshold.
│
├─ JMH scores are stable but measureTimedValue/k6 fails?
│   → Bottleneck is DB/networking. Profile with JFR to find the cause.
│   → If it's an intentional new DB query or API call, raising is OK.
│
└─ Added a new feature that legitimately changes the work being measured?
    → Raising is OK. Follow the adjustment procedure below.
```

**Adjustment procedure:**

1. **JMH baseline** — Run `./gradlew :backend:jmh`. Compare against `backend/jmh-baselines.md`.
   If scores changed, update the baseline file with new scores and note why in the commit message.

2. **measureTimedValue threshold** — Run the test 5 times and take the p95. Double it for the
   new threshold (to leave headroom). Update the threshold constant in the test file and update
   `backend/jmh-baselines.md`.

3. **k6 threshold** — Run k6 3 times and take the worst p95. Add a 50% buffer for the
   new threshold. Update `options.thresholds` in `scripts/load-test/baseline.js` and the
   table in `scripts/load-test/results/baseline-results.md`.

4. **Commit message** — Include the tool, the old threshold, the new threshold, and a brief
   justification. Example:
   ```
   perf: raise remittance submit threshold 15s→20s
   
   Added branch_day status batch update in submit path. JMH scores stable.
   p95 from 5 runs: 13.2s → threshold set to 20s.
   ```

**Baseline reference files:**

| File | What it tracks |
|---|---|
| `backend/jmh-baselines.md` | JMH scores + measureTimedValue + k6 thresholds (single source of truth) |
| `scripts/load-test/results/baseline-results.md` | k6 threshold history and run instructions |
| `scripts/load-test/results/latest.json` | k6 raw JSON output from last run (gitignored) |
