# CR-016: JVM clock (`OffsetDateTime.now()`) instead of DB clock in service/repo layer

**Source:** Chunks 2a (standards hard), 2b (standards hard)

**What:**
Multiple service and repository methods use `java.time.OffsetDateTime.now()` instead of Exposed's `CurrentTimestampWithTimeZone` (DB server clock). JVM clock and DB clock may diverge (timezone, drift). Violates `backend/AGENTS.md` "Timestamp consistency" rule.

**Chunk 2a:** JVM clock found in 3 repositories:
- `ClientRepository` — `createdAt`/`updatedAt` timestamps
- `SessionRepository` — session creation timestamps
- `BranchDayRepository` — day resolution

**Chunk 2b:** `OffsetDateTime.now()` without zone specified (uses system default timezone)

**Spec reference:** `docs/architecture_implementation_plan.md` §F3: "All timestamps are stamped server-side. The server records `now()` (UTC, stored as `TIMESTAMPTZ`)."

**Files:** (investigate and list all) — search for `OffsetDateTime.now()`, `LocalDateTime.now()` in `backend/src/main/kotlin/com/companyb/companyapp/repository/` and `service/`

**Fix:**
1. Replace `OffsetDateTime.now(ZoneOffset.UTC)` / `LocalDateTime.now()` with Exposed DSL `CurrentTimestampWithTimeZone` inside `transaction {}` blocks
2. Exception: `insertIgnore` blocks where default expression is suppressed — keep `OffsetDateTime.now(ZoneOffset.UTC)` (explicitly with zone)
3. Verify no JVM clock remains after fix: `rg "\.now\(\)" backend/src --include="*.kt"`

**Priority:** medium
**Story alignment:** cross-cutting — affects audit accuracy on all writes

**Status:** ✅ done
