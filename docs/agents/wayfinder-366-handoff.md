# Handoff - Map #310, Session 366

## Authority

- Map #310 remains continuation authority for retained Map #180 work.
- Loaded `/wayfinder`, Map #310 handoff, tracker operations, `CONTEXT.md`, module guidance, gate docs, and current hook/CI policy.

## Session outcome

- Claimed and resolved [Slim pre-commit to fast local feedback](https://github.com/jsongalvez/company_app/issues/308).
- `.githooks/pre-commit` now performs staged Kotlin formatting, staged shell syntax checks, and changed-module compile/static checks.
- Full tests, OpenAPI, integration, target matrices, test-data cleanliness, and Postgres access remain CI/integration responsibilities.
- Updated `CONTRIBUTING.md` and `backend/AGENTS.md`.
- Resolution comment: https://github.com/jsongalvez/company_app/issues/308#issuecomment-5361707583.
- Commit: `86ef7576` (`perf: slim pre-commit checks ref #308`).

## Verification

- `bash -n .githooks/pre-commit scripts/classify-push-files.sh scripts/pre-commit-docs-only-test.sh`: PASS.
- `bash scripts/pre-commit-docs-only-test.sh`: PASS.
- Changed-module Gradle compile/static checks: PASS in 69.9s.
- Real pre-commit path: PASS in 56ms for shell/docs change; no database access.
- Commit pre-commit hook: PASS.

## Next frontier

- Parent [Adopt PR-based integration and slim local gates](https://github.com/jsongalvez/company_app/issues/305) remains open and assigned.
- Next child: [Make CI own complete PR integration gates](https://github.com/jsongalvez/company_app/issues/309).
- Do not select #304, #301, or #311 until #305 is resolved or explicitly released.

## Required next action

Query native child state and verify #309 before claiming exactly one child. Read Map #310 first. Resolve #309 only; then write successor handoff.

**Status:** #308 resolved and closed; #309 is next rollout child.
