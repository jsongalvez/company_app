# Handoff - Architecture Map #180, Session 127

## What this is

Session 127 resolved [Build: migrate remaining route tests to class-scoped Javalin lifecycle](https://github.com/jsongalvez/company_app/issues/202). The next frontier child is [Build: enforce OpenAPI verification in mandatory gates](https://github.com/jsongalvez/company_app/issues/196).

## Session outcome

- Loaded Map #180, latest handoff, every Map Context Pointer, `/wayfinder`, `/codebase-design`, project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`, `CONTEXT.md`, backend conventions, business requirements, Javalin framework guidance, audit guidance, decision-loop guidance, and relevant test/database ADRs.
- Confirmed clean worktree and remote state, then claimed #202 before investigation.
- Migrated all 206 remaining `JavalinTest.test` calls across 13 backend route suites to `JavalinTestServerRule` class-scoped lifecycle.
- Preserved `company_app_test` serialization, `BasePostgresTest` per-test tracked cleanup, request isolation, route coverage, and post-test cleanliness.
- Replaced duplicate per-method JWT app builders with conditional real-token filtering in the shared class app, preserving unauthenticated 401 coverage.
- No ADR was needed; existing ADR-0004 and ADR-0006 cover tracked teardown and test-database isolation, while #200 established the lifecycle pattern.
- Posted resolution comment and closed #202. Appended its decision pointer to Map #180 Decisions-so-far.

## Verification

- Targeted route authorization suites passed in 3m19s after database cleanup.
- Full `./gradlew :backend:detekt :backend:ktlintCheck :backend:test` passed: 897 tests, 10m15s test task.
- `./gradlew :shared:compileKotlinJvm` passed.
- Test database cleanliness passed after targeted and full suites.
- Pre-commit passed formatting, detekt, ktlint, backend tests, shared compilation, cleanliness, and Postgres connectivity.
- Pre-push passed Compose Android compilation, backend distribution build, health check, k6 baseline with 0% errors and all thresholds, and final test-database cleanup.
- Baseline from #202 was 13m31s backend test task / 16m16s wall; affected-suite examples were RemittanceAuthzTest 60.989s, ReportsReadScopeAuthzTest 54.518s, and ReliefInviteAuthzTest 41.474s.

## Commit and remote

- `7319902` (`perf: reuse remaining Javalin test servers`) is present locally and remotely.
- Remote: `origin/ralph/company-app-full-build`.
- Final worktree is clean; local branch matches remote.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. Confirm commit `7319902`, remote, and clean worktree.
3. Query Map #180 children and frontier again. Claim exactly one ticket before work with `gh issue edit <n> --add-assignee @me`.
4. Select [Build: enforce OpenAPI verification in mandatory gates](https://github.com/jsongalvez/company_app/issues/196) if it remains open, unblocked, and unclaimed. Read its body and all relevant Context Pointers before editing. Do not resolve more than one ticket.
5. Treat #196 as build-gate work: preserve ordered normalization before verification, fail-closed markers, one shared gate across pre-commit, pre-push, and CI, parser ownership, and negative drift coverage.
6. Finish targeted/full validation, recovery, tracker resolution, commit, push, and cleanliness work before writing the next numbered handoff. After writing it, stop immediately.
