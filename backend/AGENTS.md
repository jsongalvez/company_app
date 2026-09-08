# Backend (Kotlin + Javalin + Exposed)

## Core Documents

Before coding, read the relevant doc(s):

| File | Use when |
|------|----------|
| `docs/architecture.md` | Architecture decisions, tech stack, backend layering |
| `docs/agents/context-discovery.md` | Starting any backend ticket — build the minimum working set progressively (search broadly, read narrowly) |
| `docs/deep-modules.md` | The backend module router — owning module, anchors, public seams, expansion triggers |
| `docs/engines.md` | Exact pseudocode or SQL for the commission, delegate, or remittance engines |
| `docs/business-requirements.md` | Domain terminology, business rules, or constraints |
| `docs/javalin-framework.md` (at `backend/docs/javalin-framework.md`) | Before writing Javalin routes, handlers, or tests — Javalin 7.x API reference |
| `CONTEXT.md` | Domain glossary and precise terminology |

## Schema

`V1__full_schema.sql` (structural baseline: #370 squash, #461 refold, #548 refold
absorbing retired V3 pg_stat_statements / V4 credential_version / V5 notification
dedup-index structure; V6 data-only backfill retired with no surviving structure)
and `V2__seed_roles_capabilities.sql` (all seeds: base
bundle + V5 scheduler capability + V22 base-rate backfill data + V26 catalog capability) in
`backend/src/main/resources/db/migration/` are the authoritative current schema — V1+V2 alone.
Inspect V1+V2 when reasoning about schema; evolve by adding new versioned migrations on top.
Existing dev/test databases are rebuilt from V1+V2 (no production database exists); never resurrect
the folded files. Databases carrying retired V3–V6 history fail Flyway validation —
reset them via docs/architecture.md §10, never Flyway-repair the history to match.

Package root: `com.companyb.companyapp`. Feature packages own their commands, internal
stores, and colocated Exposed `Table`/`View` mappings (`identity`, `authorization`,
`branch`, `branchday`, `workforce`, `client`, `session`, `commerce`, `finance`,
`commission`, `remittance`, `reporting`, `notification`, `audit`); platform support:
`api`, `http`, `app`, `database`, `exception`, `utils`, `logging`, `observability`.

## Quality gate

Git hooks are bookkeeping, not build pipelines (map #329). Pre-commit formats staged
Kotlin with the standalone ktlint CLI and syntax-checks staged shell files; pre-push
runs no gates. Neither hook starts Gradle, Postgres, the backend, k6, JMH, OpenAPI
generation, or Compose compilation, and neither queries the database.

Compile, static analysis, tests, contracts, and DB checks are **targeted and
agent-invoked** while implementing — run the smallest warm task that answers the
current question. Full validation runs asynchronously in CI; the active session never
waits for it. The anti-slop Detekt rule policy (`config/detekt/detekt.yml` +
`detekt-anti-slop.yml`) is unchanged; only its mandatory local execution schedule is
retired.

## Targeted validation

Local validation is agent-invoked, targeted, informational (map #329). Run the smallest
warm task that answers the current question, once per meaningful slice — never again just
because commit/push is next. `bash tools/quality/validate.sh` auto-selects from changed files;
pass gradle args to override. Keep the daemon and configuration/build caches warm: no
`--no-daemon` without a documented isolation reason.

| Change | Narrowest useful check |
|---|---|
| Backend pure-service logic | `./gradlew :backend:test --tests '<Fqcn>'` |
| Backend DB/transaction/locking change | focused `*PostgresTest` (owned schema per JVM + automatic per-test reset — no manual cleanup) |
| Backend route/DTO contract change | `./gradlew :backend:verifyOpenApiContract` |
| Shared DTO/domain type | `./gradlew :shared:compileKotlinJvm :shared:jvmTest` |
| Compose desktop UI only | `./gradlew :composeApp:compileKotlinDesktop` (+ `:composeApp:desktopTest` for VM logic) |
| Build logic / detekt config | compile one target per module; Detekt on affected modules |
| Docs-only | nothing |

Combine tasks into one invocation when one piece of evidence genuinely needs several
(`./gradlew :backend:compileKotlin :backend:test --tests '...'`) — do not pair compile +
Detekt + `ktlintCheck` + tests merely because they exist.

- **Detekt** runs locally only when informative: changed module/source set, Detekt config
  change, or a suspected violation — narrowest task (`:backend:detekt`,
  `:composeApp:detektDesktopMain`, …), warm daemon. Full multi-platform Detekt coverage is
  asynchronous CI work, never a local ticket step.
- **`-PwarningsAsErrors=true`** is targeted evidence, not a ticket-completion ritual.
- **OpenAPI** verification is local only for route/DTO/contract work; otherwise asynchronous.
- **k6/JMH** are manual diagnostics run when performance is the ticket's question.

Opt-in broad sweep (explicitly wanted, e.g. verifying a wide refactor):

```bash
./gradlew :backend:detekt :backend:test :shared:jvmTest :composeApp:desktopTest -PwarningsAsErrors=true
```

**JMH does not run in local hooks and does not run automatically in CI** — it is a manual
diagnostic (`workflow_dispatch` on `.github/workflows/jmh.yml`, #333/#334): dispatch it when a
performance question actually exists. A single failing baseline comparison re-runs once and
warns; the check fails only when the regression reproduces across two runs. CI-runner scores
are noisy across runs (shared-runner CPU lots vary ~1.6×), and `backend/jmh-baselines.md` holds
the per-benchmark **medians of repeated clean CI runs** — after a runner baseline shift, dispatch
the workflow several times and recompute the medians (see the workflow's comment and the
baseline file's header).

Install hooks once: `bash tools/quality/setup-hooks.sh` (sets `core.hooksPath = .githooks`).

`-PwarningsAsErrors=true` is available for targeted local compiles and stays on in
asynchronous CI. Warnings must be fixed, not suppressed or baselined.

Postgres test database hosts one owned `test_w_*` schema per backend test JVM (#493).
Parallel `./gradlew :backend:test` workers no longer race — each owns disjoint tables.
`bash tools/database/clean-test-db.sh` remains for k6/manual `public` cleanup only, never for
backend workers (their schemas drop on JVM shutdown; a killed worker leaves its uniquely
named schema behind and never scans/deletes others).

Auto-fix formatting: `./gradlew :backend:ktlintFormat`.

Run the app: `./gradlew :backend:run` (requires Postgres at `DB_HOST:DB_PORT`).

- **detekt `MagicNumber` is strict.** Extract every literal (column widths, pool sizes,
  durations, lengths) into a `private const val`; constants are exempt. `ReturnCount` max is 3
  with guard clauses excluded (Phase-A freeze, #532 — config/detekt/*.yml, pinned by
  `DetektConfigParityTest`); use a narrow `@Suppress("ReturnCount")` with a `#<ticket>` link
  when a guard-clause style is clearer (precedents: `InventoryService`, `RemittanceService`).
- **Comment hygiene (#460, scoped — never a blanket ban).** Delete redundant what-comments;
  keep why/contract notes (insertIgnore DEFAULT suppression, forUpdate terminal-op requirement,
  audit-ownership derivation) and decision pointers (`#<ticket>`, ADRs). Encode cheap constraints
  in types/tests/lint instead of prose. `ForbiddenComment` (TODO/FIXME/STOPSHIP) stays on.
  No comment-count gate, no `no-comments` CI rule — per-diff review only.
- **`*CreateParams` parameter objects.** Repository `insert`/`create` functions with 4+ non-PK parameters
  must use a `*CreateParams` parameter object (e.g. `NotificationCreateParams`) co-located in the same model
  file. 2-3 stable params can stay raw — no blanket rule. Use named-arg construction at call sites.
- detekt needs the `detekt-formatting` plugin (wired in the root `build.gradle.kts`
  `subprojects { dependencies { add("detektPlugins", ...) } }`).

## Routes & authentication

Register **public** routes (login/register) directly (e.g. `/auth/login`). Register **protected**
routes under `/api/*` so the `config.routes.before("/api/*")` filter authenticates the JWT and sets
the caller's id via `context.attribute("userId", ...)` (a `String` JWT subject). Read it back with
`context.callerUuid()` (defined in `api/ContextExtensions.kt` — uses `runCatching { UUID.fromString(...) }`
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
    val remittance = RemittanceRepository.findById(remittanceId) ?: throw NotFoundException(...)
    // ... check remittance status ...
    return RemittanceLineRepository.softDelete(lineId, callerId, remittance.version)
}

// CORRECT — pass parentId to repository and include it in the WHERE clause
fun removeLine(callerId: UUID, remittanceId: UUID, lineId: UUID): Line {
    val remittance = RemittanceRepository.findById(remittanceId) ?: throw NotFoundException(...)
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
`active_user_capabilities` SQL view (final folded shape in `V1__full_schema.sql`, covering the
V21 branch-derivation widening, V25 OWNER GLOBAL read leg, and V26 catalog leg).
**Never check roles directly in business logic** — roles seed capabilities in V2 (V5/V26 seeds folded in).
The view already excludes
INACTIVE users and out-of-window grants.

GLOBAL-scoped capabilities (`MANAGE_USERS`, `ASSIGN_DELEGATE`, GLOBAL `VIEW_BRANCH_DATA`,
GLOBAL `ASSIGN_COMPENSATION`, and GLOBAL `MANAGE_CATALOG` — the shared product/category
catalog authority, derived for SUPERUSER/OWNER/MANAGER/COORDINATOR per V26) have no specific branch/day; pass
`CapabilityContextType.GLOBAL` with `contextId = CapabilityService.GLOBAL_CONTEXT_ID` (the nil
all-zero UUID).

**Day-scoped gates (#157).** Relief grants are written as `(EDIT_BRANCH_DATA, BRANCH_DAY, branchDayId)`
with a `validFrom`/`validTo` window (`ReliefAccessRepository.grantWithCapability`; the window is
enforced by the `active_user_capabilities` view). The day-scoped write surface — expenses
(create/read/update/delete/restore), product-sale create, session create + mutations — gates via
`CapabilityFilter.requireBranchOrBranchDayCapability`: a BRANCH grant at the day's branch OR a
BRANCH_DAY grant for the specific branch day satisfies it (the day-scoped grant satisfies the gate
for that day only). Session create resolves today's day **find-only** (`BranchDayService.findToday`
— never creates in a filter). GLOBAL grants never satisfy these gates (the #131 strictness — the
OR adds only the narrower day-scoped form). Not relief-eligible (documented #157 decisions):
inventory movements (branch-scoped — the movement's day comes from the body while the route is
branch-scoped via the path; the parent-child scoping trap), allowances/compensations
(`ASSIGN_COMPENSATION`), commission (`VIEW_BRANCH_DATA`/`ASSIGN_COMPENSATION`/`EDIT_PAST_DAY`),
remittance (`SUBMIT_REMITTANCE`), session void/unvoid (`VOID_SESSION`), and (pre-#158) the branch-day status
read (`GET /api/branches/{branchId}/today`). **#158**: the ride landed — the single-day summary read
(`GET /api/branches/{branchId}/daily-summary?date=`, `requireBranchOrGlobalOrBranchDayCapabilityForBranchId`)
and `/today` (via `findToday` → `requireBranchOrBranchDayCapability`) now accept the day grant (find-only
day resolution; a missing day row means no day grant can exist — the summary read falls back to the
branch/global leg, `/today` to the plain BRANCH gate, and GLOBAL never passes `/today` — the #131
strictness, unchanged). The
multi-day browse (`/daily-summaries`) stays VIEW_BRANCH_DATA-only: a single-day grant cannot authorize an
unbounded list.

Grant writes (`user_capability` rows for relief access or delegate assignments) go only
through the authorization seam (`AuthorizationGrants.grantReliefCapabilityInTransaction`,
`grantDelegateCapabilityInTransaction`, and friends) inside the owning command's transaction —
never by touching `UserCapabilityTable` directly. Callers supply target user, branch/day,
source identity and (for relief) the validity window; the fixed capability code/ID,
source/context and priority stay owned
inside authorization (`GrantStore`, #606). The grant table and `GrantStore` stay `internal` to the
authorization owner; test fixtures seed grants via `IdentityFixtures`.

**Read endpoints must also gate on capabilities.** If a write endpoint (POST/PATCH/DELETE) checks a
capability, the corresponding read endpoint (GET) should check the same capability. Example:
`ExpenseService.create` and `ExpenseService.softDelete` gate on `EDIT_BRANCH_DATA`, so
`ExpenseService.findByBranchDayId` must also check `EDIT_BRANCH_DATA`.

## Audit logging

Every mutating service must write an audit row via `AuditLog.record(tableName, recordId,
action, changedBy, oldValue?, newValue?, reason?)`. `record` does **not** open its own `transaction {}`
— it runs the insert on the current connection and must be called inside an existing `transaction {}`.
This ensures the audit insert commits atomically with the mutation it describes.

All mutating modules are command-owned ([ADR-0024](../docs/adr/0024-command-owned-mutation-transactions.md);
the ADR-0013 `auditFn` callback was retired program-wide by map #317 / #323 and is pinned out by
`SemanticOwnershipArchitectureTest` plus per-feature ownership tests): the service command opens
exactly one transaction, repository mutators are `*InTransaction` store operations that open no
transaction, and the command calls `AuditLog.record*` directly inside that same transaction.
Before-state capture stays transaction-local: read the entity through the store inside the command's
transaction (`findByIdInTransaction`) before writing (ADR-0019's invariant, command-owned).

Build JSON values with `AuditLog.jsonField(key, value)` (safely escaped) or use the
convenience methods `recordInsert`, `recordUpdate`, `recordDelete` which accept
and `Map<String, String>` field maps.

Immediate revocation is durable (#492): logout, password reset, and deactivation advance
the persisted `app_user.jwt_revoked_at` boundary inside their command transaction, and
`JwtService.verifyToken` accepts a token only through the single `UserRepository.authorize`
read (ACTIVE status plus issuance strictly after the boundary). No process-local state.

## HTTP errors & day state

Signal business errors by throwing domain exceptions from the service layer:
`ValidationException` (400), `NotFoundException` (404), `ConflictException` (409),
`ForbiddenException` (403).
Repository-layer guards inside transactions may throw the same domain exceptions where
the check+write must stay atomic (precedents: the PENDING-session guard, the
duplicate-remittance-line 409, `VersionMismatchException`).
These are defined in `com.companyb.companyapp.exception` and are mapped to HTTP status codes
by a centralized exception handler in `Main.kt` (`registerExceptionHandlers`).
Route handlers may still throw Javalin HTTP exceptions for request-validation concerns
(BadRequestResponse, UnauthorizedResponse, ForbiddenResponse).

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

- Every table and view needs an Exposed `Table` / `object` colocated in its feature package.
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

Session create (`POST /api/sessions`) uses an idempotent PK lookup first (`SessionRepository.findById`) to handle retries with the same UUID before checking the PENDING guard (`hasActivePendingSession`). This prevents `ConflictException` for idempotent retries.

Dashboard read (`GET /api/branches/{branchId}/dashboard/today`, `DashboardService`/`DashboardRoutes`) returns today's enriched session rows (client name, practitioner names, voided-ness via `active_session_voids`, structured concerns) plus the caller's own commission — computed live by `CommissionService`'s shared batched aggregation (clocked-in-at-sale-time + manual inclusions), NOT read from `commission_split` rows (a forced recalc on a PAST day could otherwise serve stale splits). The gate is the caller's own active clock-in at the branch today (`AttendanceService.hasActiveClockIn`) — the dashboard is universal post-clock-in and deliberately NOT capability-gated; a caller clocked in elsewhere gets 403 (no cross-branch window).

Session detail read (`GET /api/sessions/{sessionId}`, #152/#151) is a deliberate exception to the "read endpoints gate on capabilities" rule: the gate is **bearer-only** — the caller may fetch iff a notification row exists for `(sessionId, caller)`, any read state (the notification IS the authorization; the #141 ownership shape). 404 for both non-bearer and missing sessions; no day-state gate (the #138 `checkBranchDayReadable` rule governs capability-gated browsing, and notified sessions' branch days are today-or-past — an `EDIT_PAST_DAY` requirement would 403 the primary case). Response reuses `DashboardSessionResponse` via the shared `mapDashboardSession` mapper.

The `computeSessionType` pure function is extracted from the service so it can be unit-tested without a database. The prior session count excludes MEDICAL_MISSION sessions and voided sessions (via `active_session_voids` view LEFT JOIN).

For concurrency, the partial unique index `idx_client_one_pending_session` is the database-level backstop against duplicate PENDING sessions for the same client — the service pre-check (`hasActivePendingSession`) is the first line of defense, followed by the unique index. Row locks via Exposed `Query.forUpdate()` ARE available and execute only with a terminal op (`.singleOrNull()`) — `SessionRepository.acquireClientLock` / `ProductSaleRepository.acquireInventoryLock` / the RemittanceRepository lock helpers materialize them this way; a `forUpdate()` without a terminal op silently no-ops (the #136 lazy-lock bug class).

Session status update (`PATCH /api/sessions/{sessionId}/status`) uses Exposed DSL `SessionTable.update({ (id eq sessionId) and (version eq expectedVersion) })` for atomic optimistic locking — if the version doesn't match, no rows are updated and the service throws 409 Conflict. The version is incremented by setting `it[SessionTable.version] = expectedVersion + 1`. Call `AuditLog.record` inside the same `transaction {}` block. The DB has `CONSTRAINT walk_in_status CHECK (NOT (is_walk_in = true AND session_status IN ('NO_SHOW', 'CANCELLED')))` — always validate this at the service layer for a cleaner 400 error before hitting the DB constraint.

Session practitioner management (`POST/PATCH/DELETE /api/sessions/{sessionId}/practitioners`) uses the `session_practitioner` table (UNIQUE on session_id + practitioner_id) with `insertIgnore` for idempotent adds. When adding a practitioner, `slot_at_time` is snapshotted from the user's active `user_branch_assignment` at the session's branch (defaults to 999 if no assignment exists). Each practitioner mutation (add, update remarks, remove) atomically increments `session.version` using a read-then-write pattern (`select version then update to version + 1`). All mutations gate on `EDIT_BRANCH_DATA` capability and call `BranchDayService.assertEditable`.

## Clock-in / Attendance

Clock-in (`POST /api/attendance/clock-in`) is open to all authenticated users (no capability gate).
The service determines `is_relief` by checking for an active `user_branch_assignment` at the target
branch: if no assignment exists, the user clocks in as relief.

Before inserting, check for an existing active clock-in via `AttendanceRepository.hasActiveClockIn`
and throw `ConflictException` (409) if found — this prevents the unique index
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

Integration tests use a real Postgres instance via `TestDatabaseLifecycle.ensureDatabase()` (connects
via the root `.env` configuration). Each test JVM owns one `test_w_<pid>_<rand>` schema in the
dedicated test database (one Flyway migration per worker, `search_path "<owned>", public` on every
pooled connection); methods within a worker stay serial, isolation is between JVMs only (#493,
ADR-0006). Per-test isolation is one `TRUNCATE ... RESTRICT` of the owned schema before setup
and after each test (`TestDatabaseLifecycle.resetWorkerSchema`, seeds + Flyway history preserved,
snapshot trigger never toggled — #494). Scenario fixtures live in `testsupport.fixtures` families
(identity/capability, branch/workforce, session/client, commerce/finance); cross-feature lock
barriers live under `integration` (#552). Tests describe scenarios and assertions only, never
row ownership. Run all tests with `./gradlew :backend:test`. Prefer DB-free
unit tests for pure-logic helpers; inject time through internal `*At(now: Instant)` helpers.

Fixture clocks and validity windows must agree: a fixture that seeds a
`validFrom`/`validTo` window (or any time-gated state) derives it from an injectable real clock
(`TestFixtures.realNow()`), never from frozen `TestFixtures.now` — a frozen clock plus a wall-clock
window makes "expired" fixtures genuinely active for part of the UTC day (#412: a daily
00:00–02:00 red window in `ReliefDayGateAuthzTest`).

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

import com.companyb.companyapp.session.SessionService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class SomeBenchmark {

    @Benchmark
    public void myHotPath(Blackhole bh) {
        bh.consume(SessionService.INSTANCE.someMethod(input));
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

All k6 scripts live in `tools/performance/k6/`. `helpers.js` is the single source of truth for metrics,
thresholds, and auth utilities.

k6 tests MUST run against the **test database** (`company_app_test`), not the main DB.
The test DB is designed for throwaway use; clean it after each run with
`bash tools/database/clean-test-db.sh` to preserve the cleanliness invariant that
`tools/database/check-test-cleanliness.sh` verifies.

**Quick start — two terminals:**

Terminal 1 — start the app on the test DB with dev-user seeding:
```bash
POSTGRES_DB=company_app_test TEST_USERNAME=owner TEST_PASSWORD=pass ./gradlew :backend:run
```

Terminal 2 — once the app is ready, run any k6 script:
```bash
# Baseline load test (branches, clients search, products — staged ramp-up)
TEST_USERNAME=owner TEST_PASSWORD=pass k6 run tools/performance/k6/baseline.js

# Full load test (all endpoints, 5 VUs ramp-up)
TEST_USERNAME=owner TEST_PASSWORD=pass k6 run tools/performance/k6/full-suite.js

# Concurrency edge cases (pending guard, version mismatch, idempotency)
TEST_USERNAME=owner TEST_PASSWORD=pass k6 run tools/performance/k6/concurrency-test.js

# Authz edge cases (invalid token, expired token, insufficient capability —
# the 403 principal is the DevSeeder branch-scoped user, #411; full-suite also
# runs branch-scoped leg group when SCOPED_USERNAME/SCOPED_PASSWORD are set)
SCOPED_USERNAME=scoped SCOPED_PASSWORD=scopepass k6 run tools/performance/k6/authz-test.js

# Concurrent remittance submission (serializable isolation race)
TEST_USERNAME=owner TEST_PASSWORD=pass k6 run tools/performance/k6/remittance-race-test.js
```

After the k6 run, clean the test DB to preserve the cleanliness invariant:
```bash
bash tools/database/clean-test-db.sh
```

Run this workflow manually when load-testing is the ticket's question; hooks and CI do
not start the backend or run k6 for you (#333/#335 — the hosted k6 workflow was removed, and
broad/load suites are manual diagnostics).

The baseline uses `thresholdProfiles.baseline` from `tools/performance/k6/helpers.js`. Edit the named profile
there to adjust thresholds; suites consume profiles and do not own threshold values:
- `branches_latency`: p95 < 500ms
- `clients_search_latency`: p95 < 1000ms
- `product_latency`: p95 < 1000ms
- `errors`: rate < 5%

**Adding a new endpoint to the baseline** — edit `tools/performance/k6/baseline.js` and
`tools/performance/k6/helpers.js`:
1. Use an existing metric from `helpers.js` or add a new `Trend` to `metrics` in `helpers.js`
2. Add the metric threshold to the appropriate named `thresholdProfiles` entry in `helpers.js`
3. Add the `http.get`/`http.post` call in the `default` function
4. Run `k6 run` to establish a baseline p95, then tighten the threshold

Remember: k6 tests the full HTTP stack — serialization, Javalin routing, JDBC, connection
pooling, and auth middleware. Regressions here won't show up in JMH benchmarks.

### Threshold tuning — when and how to adjust limits

All tools (JMH, measureTimedValue, k6) have hardcoded thresholds. **Do not blindly raise a
threshold to make a failing test pass.** Follow this decision tree:

```
Threshold violation detected
│
├─ JMH scores for the same hot path dropped below threshold (default 20%, 40% for BranchDayBenchmark.*)?
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
   new threshold. Update the relevant named `thresholdProfiles` entry in
   `tools/performance/k6/helpers.js` and record the resulting threshold/history in
   `tools/performance/k6/results/baseline-results.md`.

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
| `backend/jmh-baselines.md` | JMH scores and measureTimedValue thresholds |
| `tools/performance/k6/helpers.js` | k6 metrics and named threshold profiles (runtime source of truth) |
| `tools/performance/k6/results/baseline-results.md` | k6 threshold history and run instructions |
| `tools/performance/k6/results/latest.json` | k6 raw JSON output from last run (gitignored) |
