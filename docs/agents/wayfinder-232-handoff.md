# Handoff - Architecture Map #180, Session 129

## What this is

Session 129 resolved [Build: unify persistence timestamp authority](https://github.com/jsongalvez/company_app/issues/197). The next frontier child is [Build: delete unused SessionState.isLoggedIn](https://github.com/jsongalvez/company_app/issues/198).

## Session outcome

- Loaded Map #180, latest handoff, every Map Context Pointer, `/wayfinder`, `/codebase-design`, project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`, domain/backend/shared/build documents, audit and decision-loop guidance, and relevant timestamp/rate/attendance/relief ADRs.
- Retried publication before claiming work; mandatory local gates passed, but GitHub rejected push because active PAT lacks `workflow` scope. Claimed #197 before investigation.
- Made PostgreSQL the persistence timestamp authority for scoped R13 paths. Rate rollover now deactivates prior rates and inserts replacement in one transaction using `CurrentTimestampWithTimeZone`; active-rate reads use DB time too.
- Replaced JVM timestamps in `insertIgnore` attendance clock-in, relief capability grant, and relief invite creation with explicit `CurrentTimestampWithTimeZone` expressions. Preserved Manila calendar ownership and did not add universal clock abstraction.
- Added rate-boundary validation asserting persisted prior `effectiveUntil` equals replacement `effectiveFrom`.
- Posted resolution comment and correction, closed #197, and appended its decision pointer to Map #180 Decisions-so-far.
- No ADR needed; existing timestamp and rate-gap decisions remain satisfied.
- Commit message includes requested issue reference: `cba8c03` uses `ref #197`. Future commits must include issue reference in commit message.

## Verification

- Targeted timestamp-related suites passed after test database cleanup.
- Full `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm` passed.
- Pre-commit passed formatting, detekt, ktlint, backend tests, shared compilation, OpenAPI gate, cleanliness, and Postgres connectivity.
- Pre-push passed OpenAPI gate, Compose Android compilation, backend distribution build, health check, k6 baseline with 0% errors and all thresholds, and final test-database cleanup.
- Final test database cleanliness passed.

## Commit and remote

- `cba8c03` (`fix: use database persistence timestamps`, `ref #197`) is committed locally.
- Push was attempted after all hooks. GitHub rejected it: `refusing to allow a Personal Access Token to create or update workflow .github/workflows/openapi.yml without workflow scope`.
- `gh auth status` confirms active PAT scopes are only `read:org, repo`; remote remains at `7319902`. Local branch is ahead by existing checkpoint commits plus `cba8c03`.
- Worktree is clean before this handoff file is written.

## How to drive next session

1. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
2. First retry publishing local commits after obtaining PAT `workflow` scope; verify remote contains `cba8c03`. Do not rewrite or drop commits. Keep issue references in every commit message, e.g. `ref #<issue-number>`.
3. Query Map #180 children/frontier. #197 is closed; select and claim exactly one next open, unblocked, unclaimed child. Expected next pick is #198.
4. Read #198 body and relevant Compose/session state documents before editing. Delete only confirmed unused `SessionState.isLoggedIn` declaration and `GlobalScope` machinery; preserve real session lifecycle behavior.
5. Run targeted checks, full backend/shared gates, cleanliness, and required pre-push gates. Repair local failures and retry external push.
6. Resolve tracker issue, update Map #180, commit with issue reference, and push before writing next numbered handoff. After writing it, stop immediately.
