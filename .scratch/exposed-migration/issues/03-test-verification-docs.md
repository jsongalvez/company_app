# EM-03 — Full test suite verification + docs update

**What to build:** Run the full quality gate against the migrated codebase, fix any remaining compilation or runtime errors, verify `insertIgnore` + `insertedCount` behavior (known 0.61.0 bug with `autoGenerate()` PKs should be fixed in 1.3.1), and update documentation.

**Blocked by:** EM-01, EM-02

**Status:** ready-for-agent

- [ ] Run quality gate: `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :backend:jmhClasses`
- [ ] Fix any test failures — pay special attention to: `insertIgnore` + `insertedCount` return values (80 call sites), join syntax (both 3-arg and 6-arg `innerJoin`/`leftJoin`/`join` forms), `orderBy` patterns, `forUpdate(ForUpdateOption.ForUpdate)` syntax
- [ ] Verify `customEnumeration` columns read/write correctly at runtime (integration tests use real Postgres via `DatabaseTestHelper`)
- [ ] Verify the `/health` endpoint still works (pre-commit hook health check)
- [ ] Run `./gradlew ktlintFormat` to auto-fix any formatting issues from import adjustments
- [ ] Update `backend/AGENTS.md`:
  - Update import path examples from `org.jetbrains.exposed.sql.*` to `org.jetbrains.exposed.v1.*`
  - Update `exec()` note to reflect it's on `JdbcTransaction`
  - Update table definition examples if `uuid()` → `javaUUID()` changes apply
  - Update `Query.forUpdate()` note if API changed
  - Add note about `SqlExpressionBuilder` replacement (top-level functions, `import org.jetbrains.exposed.v1.core.*`)
- [ ] Update `docs/design_specification.md` if it references Exposed version
- [ ] Run full pre-commit hook to verify end-to-end: `bash .githooks/pre-commit`
