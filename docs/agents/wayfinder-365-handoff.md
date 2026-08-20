# Handoff - Map #310, Session 365

## Authority

- Map #310 is continuation authority for retained Map #180 work.
- Loaded `/wayfinder`, Map #310 handoff, tracker operations, `CONTEXT.md`, and current workflow documentation.

## Session outcome

- Claimed and resolved [Document branch, PR, and merge policy](https://github.com/jsongalvez/company_app/issues/306).
- Added `README.md` Contribution Workflow documentation covering short-lived `ralph/<feature-name>` branches, PRs targeting `master`, review, applicable CI checks, normal merge expectations, docs-only pushes, gate-sensitive pushes, and emergency/AFK handling.
- No production code or workflow behavior changed.
- Map #310 Decisions so far now links #306.

## Evidence

- `git diff --check -- README.md`: passed.
- Current applicable CI ownership verified in `.github/workflows/quality.yml`, `.github/workflows/openapi.yml`, and `.github/workflows/jmh.yml`.
- Current docs-only and gate-sensitive pre-push behavior verified in `.githooks/pre-push` and `AGENTS.md`.

## Next frontier

- Parent [Adopt PR-based integration and slim local gates](https://github.com/jsongalvez/company_app/issues/305) remains open.
- Next available rollout children are [Slim pre-commit to fast local feedback](https://github.com/jsongalvez/company_app/issues/308) and [Make CI own complete PR integration gates](https://github.com/jsongalvez/company_app/issues/309).
- Do not select #304, #301, or #311 until #305 is resolved or explicitly released.
- Claim exactly one next frontier child before work; suggested next ticket is #308, subject to current native dependency and assignee query.

## Verification

- No Gradle, database, or application gate run; documentation-only change.

**Status:** Branch, PR, and merge policy documented; rollout gate work pending.
