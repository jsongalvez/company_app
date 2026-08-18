# Handoff - Architecture Map #180, Session 107

## What this is

Session 107 resolved [Build: remove redundant client trigram indexes](https://github.com/jsongalvez/company_app/issues/184). The ticket was claimed before investigation, verified against representative query plans, implemented, validated, committed, pushed, resolved, and linked from Map #180.

## Session outcome

- Captured representative plans on 100,000 disposable test clients before and after removing the single-column indexes.
- Confirmed `idx_client_trgm` covered all first-name, middle-name, and last-name `ILIKE` predicates; the plan remained a `BitmapOr` over the composite index.
- Added `V20__remove_redundant_client_trigram_indexes.sql` with idempotent drops and commented rollback `CREATE INDEX` statements.
- Confirmed `similarity()` predicates use sequential scans and do not depend on the removed indexes.
- Closed [Build: remove redundant client trigram indexes](https://github.com/jsongalvez/company_app/issues/184) after posting resolution evidence.
- Updated Map #180 `Decisions so far` with the migration decision.
- Configured OpenCode `general` and `explore` subagents to use `openai/gpt-5.6-luna#max`.

## Recovery

- First aggregate quality-gate attempt timed out after 120 seconds.
- Second attempt reached ten minutes and reported four unrelated authorization test failures.
- Cleaned disposable `company_app_test`; isolated reruns of `AuditLogAuthzTest`, `ReportsReadScopeAuthzTest`, and `UserManagementAuthzTest` passed.
- Retried aggregate gate successfully in 13m22s.

## Verification

- Targeted `ClientServicePostgresTest` passed.
- Full `:backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm` passed.
- Test-data cleanliness passed after targeted tests, aggregate tests, commit hook, and pre-push hook.
- Pre-commit quality gate passed.
- Pre-push Compose Android compilation passed.
- Pre-push k6 baseline passed with zero errors; `clients_search_latency` p95 was 85.47 ms.
- `git diff --check` passed.
- Two read-only review subagent calls were attempted but unavailable: `Model unavailable: opencode-go/gpt-5.6-luna`. Manual migration review found no issue.

## Tracker state

- [Map: Ideal architecture for business requirements](https://github.com/jsongalvez/company_app/issues/180) remains OPEN and permanent.
- [Build: remove redundant client trigram indexes](https://github.com/jsongalvez/company_app/issues/184) is CLOSED.
- [Build: type finite values in shared wire DTOs](https://github.com/jsongalvez/company_app/issues/191) remains OPEN and unclaimed.
- Other open task children include [Build: centralize k6 threshold profiles](https://github.com/jsongalvez/company_app/issues/187), [Build: share OpenAPI source parser](https://github.com/jsongalvez/company_app/issues/188), [Docs: repair architecture and ADR source pointers](https://github.com/jsongalvez/company_app/issues/185), and [Build: make JWT runtime initialization atomic](https://github.com/jsongalvez/company_app/issues/186).
- Next session must load Map #180, select the first live unblocked child by existing order, and claim exactly one ticket before investigation or edits.

## Commit and remote

- Commit `69f2da5` (`perf: remove redundant client trigram indexes`) is pushed to `origin/ralph/company-app-full-build`.
- Remote branch currently points to `69f2da576510bca88846b35db4de5fa95d4a8abc`.
- Repository worktree is clean.

## Recommended skills

- `/wayfinder` — load and follow Map #180 frontier, claim, resolution, and handoff rules.
- `/implement` — execute the next AFK build ticket per its module instructions.
- `/code-review` — review implementation changes on Standards and Spec axes before resolution.
- `/diagnosing-bugs` — use when local gates or runtime behavior fail; repair root cause before stopping.
- `/writing-for-agents` — required before editing handoffs or other agent-facing documentation.

## Map #180 context pointers

Every session working on Map #180 must read the map's `Context Pointers` and load every applicable pointer before investigation or edits:

- `CONTEXT.md`
- `docs/business-requirements.md`
- `docs/agents/audit-your-codebase.md`
- `/improve-codebase-architecture`
- `/codebase-design`
- `docs/agents/issue-tracker.md`
- Relevant module `AGENTS.md` and area ADRs

## How to drive the next session

1. Load Map #180 and this handoff; choose the first live unblocked child before work.
2. Claim exactly one ticket before investigation or edits.
3. Follow local recovery policy from the runner prompt; repair recoverable local failures and retry.
4. For [Build: type finite values in shared wire DTOs](https://github.com/jsongalvez/company_app/issues/191), preserve strict uppercase wire values and leave open text and sentinel query filters such as `ALL` unchanged.
5. Create or reopen an ADR only if durable architecture evidence justifies it and the full ADR requirements pass.
6. Finish all recovery, validation, tracker, commit, and push work before writing the next numbered handoff.
7. After writing that handoff, stop.
