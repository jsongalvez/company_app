# Handoff - Architecture Map #180, Session 133

## What this is

Session 133 resolved [Build: preserve remittance submission audit before state](https://github.com/jsongalvez/company_app/issues/204), the R27 implementation child of Map #180. The next frontier child is [Build: complete iOS Compose platform bridge](https://github.com/jsongalvez/company_app/issues/205).

## Session outcome

- Loaded latest handoff, Map #180 body/comments, every Context Pointer, `/wayfinder`, `/codebase-design`, project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`, domain/backend/shared/build documents, audit and review guidance, and remittance ADRs.
- Retried publication before claiming work. First retry timed out during k6; second retry found disposable test-data leaks, repaired them with `bash scripts/clean-test-db.sh`, then passed all local pre-push gates. GitHub rejected the push because active PAT lacks `workflow` scope.
- Claimed exactly one ticket: #204. Moved Branch Day before-state capture in `RemittanceRepository.submit` before `REMITTED` mutation. Post-mutation rows are paired with captured rows inside the existing SERIALIZABLE transaction.
- Added exact Audit Log assertions for current-day OPEN and lazy-PAST Branch Day transitions. Closed #204 with resolution evidence and updated Map #180 Decisions so far. No ADR was needed.

## Verification

- Focused `RemittanceServicePostgresTest` passed with 18 tests, including current OPEN and lazy-PAST before-state assertions.
- Standard P1-P4 review initially found one HARD coverage gap; added current-day OPEN coverage. Rerun found zero HARD findings. SOFT rollback/concurrent retry suggestions were outside this narrow ticket and recorded in the resolution rationale.
- `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm` passed.
- Test-data cleanliness passed before and after targeted/full tests, pre-commit, and pre-push.
- Pre-commit passed formatting, detekt, ktlint, backend tests, shared JVM compile, OpenAPI contract checks, cleanliness, and Postgres connectivity.
- Pre-push passed OpenAPI route/secret/negative-drift checks, Compose Android compilation, backend distribution build, health check, k6 baseline with 0% errors and all thresholds, and final DB cleanup.

## Commit and remote

- `c669f62` (`fix: preserve remittance audit before state`, `Ref #204`) contains implementation and tests.
- Push was attempted after all hooks. GitHub rejected it: `refusing to allow a Personal Access Token to create or update workflow .github/workflows/openapi.yml without workflow scope`.
- `gh auth status` reports active PAT scopes `read:org, repo`; remote remains `7319902`. Local branch is ahead by thirteen commits. Do not rewrite or drop commits.
- Worktree was clean before this handoff file was written. This handoff is intentionally the final uncommitted chain signal because external push remains blocked.

## How to drive next session

1. First retry publishing local commits after obtaining PAT `workflow` scope; verify remote contains `c669f62`. Do not rewrite or drop commits. Keep issue references in every commit message.
2. Load Map #180, this handoff, every Context Pointer, `/wayfinder`, `/codebase-design`, and project-local `.opencode/skills/improve-codebase-architecture/SKILL.md`.
3. Query Map #180 children/frontier. Claim exactly one ticket: #205. Do not claim #206.
4. Read #205 body and Compose/iOS/platform ADR and bridge documents. Implement only R28: complete missing iOS actuals and Swift host wiring before claiming iOS support.
5. Run targeted checks, selected risk-based review profile, full required quality gates, cleanliness, and pre-push. Repair local failures and retry external push; record exact external evidence if auth remains blocked.
6. Resolve #205, update Map #180 and architecture lessons if needed, commit with issue reference, and push before writing next numbered handoff. After writing it, stop immediately.
