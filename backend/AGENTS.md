# Backend (Kotlin + Javalin + Exposed)

Package root: `com.companyb.companyapp`. Layers: `api/routes`, `service`, `repository`
(+ `repository/model` for Exposed `Table` objects), `auth`, `database`, `logging`.

## Quality gate (run before every commit)

```
./gradlew :backend:detekt :backend:ktlintCheck :backend:test
```

Auto-fix formatting: `./gradlew :backend:ktlintFormat`.

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

## Audit logging

Every mutating service must write an audit row via `AuditLogRepository.record(tableName, recordId,
action, changedBy, oldValue?, newValue?, reason?)`. `record` opens its own `transaction {}` which
**joins an enclosing transaction** (Exposed reuses the connection unless nested transactions are
explicitly enabled), so the audit insert commits atomically with the change it describes — call it
inside the same repository `transaction {}` that performs the mutation. Build JSON values with
`AuditLogRepository.jsonField(key, value)` (safely escaped). `old_value`/`new_value` are JSONB and
bound as text with `?::jsonb` casts.

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

## Exposed + Postgres gotchas

- Add `exposed-java-time` for `timestampWithTimeZone` / `CurrentTimestampWithTimeZone`.
- When querying Postgres `enum` columns via raw SQL, bind params as text and cast in SQL
  (e.g. `?::capability_context_type`, `?::uuid`) using
  `transaction { exec(sql, args = listOf(TextColumnType() to value)) { rs -> ... } }`.
  This avoids Exposed PG-enum operator mismatches.
- The same enum rule applies to writes: Exposed `enumerationByName` binds values as varchar, so
  updates to native Postgres enum columns need raw SQL with explicit casts (for example,
  `SET status = ?::user_status`).

## Clock-in / Attendance

Clock-in (`POST /api/attendance/clock-in`) is open to all authenticated users (no capability gate).
The service determines `is_relief` by checking for an active `user_branch_assignment` at the target
branch: if no assignment exists, the user clocks in as relief.

Before inserting, check for an existing active clock-in via `hasActiveClockIn` and throw
`io.javalin.http.ConflictResponse` (409) if found — this prevents the unique index violation on
`idx_one_active_clock_in`.

The attendance insert and `branch_day_assignment` upsert happen in a single transaction via raw SQL
(`ON CONFLICT DO NOTHING`). For idempotency, `ON CONFLICT (id) DO NOTHING` with `RETURNING id`
returns the id only on insert; when the id already exists, RETURNING returns zero rows so we detect
the duplicate and read the existing row with a follow-up `SELECT`.

## Testing

No Postgres/Docker is guaranteed in the agent sandbox, so DB integration tests may not run here.
Prefer DB-free unit tests; inject time through internal `*At(now: Instant)` helpers (see
`DenyListTest`) rather than sleeping or relying on wall-clock time.

Gradle backend tests run from the repo root (`tasks.test.workingDir = rootProject.projectDir`) so
dotenv-kotlin can load the root `.env`.
