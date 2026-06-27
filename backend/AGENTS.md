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

## Authorization

All operational permission checks MUST go through
`CapabilityService.hasCapability(userId, capabilityCode, contextType, contextId)`, which queries the
`active_user_capabilities` SQL view (defined in `V1__full_schema.sql`). **Never check roles directly
in business logic** — roles only seed capabilities in the V2 migration. The view already excludes
INACTIVE users and out-of-window grants.

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

## Testing

No Postgres/Docker is guaranteed in the agent sandbox, so DB integration tests may not run here.
Prefer DB-free unit tests; inject time through internal `*At(now: Instant)` helpers (see
`DenyListTest`) rather than sleeping or relying on wall-clock time.
