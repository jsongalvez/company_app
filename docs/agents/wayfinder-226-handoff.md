# Handoff - Architecture Map #180, Session 123

## What this is

Session 123 resolved [Audit: diagnose slow Gradle backend tests](https://github.com/jsongalvez/company_app/issues/199), the highest-priority operational follow-up selected by Map #180.

## Session outcome

- Loaded Map #180 through REST, latest handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`, backend conventions, domain requirements, architecture, Javalin lifecycle guidance, and ADR-0006.
- Claimed exactly one ticket: #199 `Audit: diagnose slow Gradle backend tests`.
- Forced full backend test run after cleaning `company_app_test`: 897 tests, one Gradle Test Executor, `:backend:test` 13m31s, wall 16m16s. XML suite time was 805.514s across 62 classes.
- Confirmed no Gradle parallelism setting exists. Parallel workers remain unsafe because all tests share `company_app_test` and tracked cleanup state.
- Confirmed `DatabaseTestHelper` creates one Hikari pool and runs Flyway once per test JVM; database setup is not repeated per test.
- Found 288 `JavalinTest.test` calls. Javalin 7.2.2 `TestTool` starts the app on port 0 and stops it around every test case. Route suites dominate runtime: full-run examples were RouteValidation 77.002s, AuditLogAuthz 65.963s, RemittanceAuthz 64.104s, ReportsReadScope 50.470s, ReliefInvite 42.618s, and ReliefDayGate 41.886s.
- Isolated confirmation passed: RouteValidation 65 tests / 107.663s; AuditLogAuthz 31 tests / 57.125s.
- Sampled `pg_stat_activity` during AuditLogAuthz. Activity was overwhelmingly `ClientRead`; no lock or IO wait states appeared. No hidden database hang found.
- Created [Build: reuse Javalin test server per route test class](https://github.com/jsongalvez/company_app/issues/200) as separate Map #180 child. It must preserve test-database serialization, request/test-state isolation, tracked cleanup, cleanliness, and coverage.
- Posted #199 resolution, closed it, and updated Map #180 Decisions so far and child list. Map #180 remains OPEN.

## Tracker state

- Map #180 remains OPEN and assigned to `jsongalvez` by design.
- #199 `Audit: diagnose slow Gradle backend tests` is CLOSED.
- #200 `Build: reuse Javalin test server per route test class` is OPEN and unclaimed; it is next.
- #195, #196, #197, and #198 remain closed implementation history.
- R15 notification inserted-count truth remains deferred pending deployment topology or overlapping scheduler invocation requirements.

## Verification

- Forced full backend test run passed.
- Isolated RouteValidation and AuditLogAuthz runs passed.
- Post-test cleanliness passed after every measured run.
- Pre-push passed test-data cleanliness, Compose Android/Desktop compilation, backend distribution build, health check, k6 baseline, and final cleanup. k6 errors were 0%; all thresholds passed.
- `git diff --check` passed.

## Commit and remote

- Confirmed `7baded9` and `c8c10ce`.
- Pushed prior handoff commit `144df83` (`docs(wayfinder): checkpoint wayfinder-225-handoff.md`) to `origin/ralph/company-app-full-build`.
- Final worktree is clean and local branch matches remote.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Confirm commit `144df83`, remote, and clean worktree.
3. Select and claim exactly one ticket before work: #200 `Build: reuse Javalin test server per route test class`.
4. Read backend conventions, `docs/agents/gates.md`, relevant Javalin guidance, test helpers, affected route test classes, and ADR-0006 before editing.
5. Design lifecycle ownership and an explicit seam that reuses configured Javalin server state without sharing request state or weakening per-test database cleanup. Do not enable parallel test workers.
6. Add focused harness regression coverage, measure affected classes and the full suite, then run targeted and integration gates. Preserve route coverage and cleanliness.
7. Resolve only #200, update Map #180, commit, push, and write the next numbered handoff only after all tracker, validation, and remote work is complete. Stop immediately after writing that handoff.
