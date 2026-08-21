# Handoff - Detekt Ratchet Child #281

## Status

- #281 remains open. Do not close parent #274 from this worktree.
- Full typed local quality command passes, including backend tests and Compose/shared
  Detekt tasks with `-PwarningsAsErrors=true`.
- Targeted expired-relief authorization test and full backend test suite pass after
  widening fixture expiry margins to absorb JVM/database clock skew.
- Gate ledger: `docs/gates/281-detekt-ratchet-closeout.md`.

## Blocking review findings

- Anti-slop safety rules have production file exclusions for `ForbiddenMethodCall`,
  `ForbiddenSuppress`, `TooGenericExceptionCaught`, `SwallowedException`, and
  `UnreachableCode`. Parent #274 explicitly forbids broad safety exclusions.
- Full P1-P5 closeout packet, rule-by-rule disposition matrix, and clean review
  evidence are not recorded. Do not claim completion from passing Gradle gates.
- File-wide Compose deprecation suppressions need explicit API migration or narrow,
  reviewed dispositions before closeout.

## Behavior fixes retained

- `SchedulerLifecycle` catches task/scheduling `Exception` without swallowing fatal
  `Error` values; candidate executor still shuts down on scheduling failure.
- Android download save remains fail-closed when application context is unavailable.

## Next action

Remove or replace safety-rule exclusions with evidence-backed narrow adaptations and
record P1-P5 dispositions. Re-run full gate ledger, then update #281 and #274 only
after zero HARD and zero unadjudicated ESCALATE findings.
