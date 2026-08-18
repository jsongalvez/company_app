# Handoff - Architecture Map #180, Session 106

## What this is

Session 106 updated wayfinder runner policy after user-directed grilling. No Map #180 ticket was claimed; the next session must select and claim exactly one frontier child.

## Session outcome

- Confirmed runner lifecycle: handoff file is sole session completion signal; no work follows handoff creation.
- Updated `scripts/wayfinder-loop.sh` spawn instructions so agents continue through recoverable local failures, repair the disposable VPS project database, retry pushes, and defer only confirmed external outages.
- Updated `README.md` resilience and environment guidance.
- Cleaned and verified disposable `company_app_test` after the prior cleanliness failure.
- Pushed commits `2eb627e` and `0bfb811` to `origin/ralph/company-app-full-build`.

## Policy decisions

- Preserve one active Map #180 ticket per session and claim-first discipline.
- Local build, test, hook, database, and push-gate failures require autonomous diagnosis and repair.
- This VPS has no production or user data for this project; its project database may be cleaned, recreated, migrated, or repaired.
- Defer only confirmed external failures such as GitHub, network, or unavailable external services.
- Create or reopen ADRs only for genuinely durable, justified architecture decisions and only when ADR requirements are satisfied.
- Complete recovery, ADR, tracker, commit, and push work before writing handoff.

## Verification

- `bash -n scripts/wayfinder-loop.sh` passed.
- `git diff --check` passed.
- Pre-commit quality gate passed.
- Pre-push Compose Android compilation passed.
- Pre-push k6 baseline passed with zero errors.
- Pre-push test-data cleanup passed before and after k6.

## Tracker state

- Issue #180 remains OPEN and permanent.
- Issue #191 remains OPEN and unclaimed for strict wire DTO implementation.
- Next frontier candidate by existing order: [Build: remove redundant client trigram indexes](https://github.com/jsongalvez/company_app/issues/184), unless live blocker or assignee changes.

## Commit and remote

- `origin/ralph/company-app-full-build` contains `0bfb811`.
- Worktree is expected clean after this handoff commit.

## Recommended skills

- `/wayfinder` — load and follow Map #180 frontier, claim, resolution, and handoff rules.
- `/implement` — execute the next AFK build ticket per its module instructions.
- `/code-review` — review implementation changes on Standards and Spec axes before resolution.
- `/diagnosing-bugs` — use when local gates or runtime behavior fail; repair root cause before stopping.
- `/writing-for-agents` — required before editing handoffs or other agent-facing documentation.

## How to drive the next session

1. Load Map #180 and this handoff; choose the first live unblocked child before work.
2. Claim exactly one ticket before investigation or edits.
3. Follow local recovery policy from the runner prompt; do not stop for repairable VPS failures.
4. For #191, preserve strict uppercase wire values and leave open text and sentinel query filters such as `ALL` unchanged.
5. Create or reopen an ADR only if durable architecture evidence justifies it and the full ADR requirements pass.
6. Finish all recovery, validation, tracker, commit, and push work before writing the next numbered handoff.
7. After writing that handoff, stop.
