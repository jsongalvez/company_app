# Handoff - Map #180 Expense Delete Race, Session 349

## Authority

- Map #180 remained workflow authority; `docs/agents/wayfinder-290-handoff.md` was state evidence only.
- Loaded `/wayfinder`, `CONTEXT.md`, architecture, business requirements, engines, decision loop, gates, issue tracker, all module instructions, and applicable Context Pointers.
- Verified native links before claim:
  - `scripts/wayfinder-verify-child.sh 180 291` -> `Verified child #291: parent #180, label wayfinder:task`
  - `scripts/wayfinder-verify-child.sh 180 292` -> `Verified child #292: parent #180, label wayfinder:task`

## Session outcome

- Claimed and completed exactly one frontier child: [Build: keep soft-deleted expenses immutable under races](https://github.com/jsongalvez/company_app/issues/291).
- `ExpenseRepository.update` now requires `deleted_at IS NULL` in its atomic `(id, version)` predicate. A stale update losing to soft delete returns established `VersionMismatchException`; deleted financial data remains unchanged.
- Added PostgreSQL regression coverage for stale update after soft delete, including amount, deletion reason, and version invariants.
- No ADR needed: change reinforces existing repository-owned optimistic locking and soft-delete rules.

## Delivery and verification

- Gate ledger `docs/gates/291-expense-delete-race.md`: 2/2 PASS. Pre-code negative control failed both gates as required.
- Focused ExpenseServicePostgresTest: PASS.
- Full backend detekt, ktlint, tests, and shared JVM compilation: PASS.
- Pre-commit quality gate, OpenAPI contract, test-data cleanliness, Compose Android/Desktop compilation, startup/health, k6 baseline with 0% errors, and disposable DB cleanup: PASS.
- P1-P4 review: zero HARD findings. One SOFT recorded: regression is deterministic post-delete coverage rather than scheduler-level interleaving; atomic SQL predicate is the race fix.
- Commit `0d1b96f` pushed to `origin/ralph/company-app-full-build`.
- Child #291 closed and resolution recorded. Map #180 Decisions-so-far pointer appended.

## Frontier

- Open, unblocked, unassigned child: #292, [Build: allow overlapping draft remittances](https://github.com/jsongalvez/company_app/issues/292).
- Next session claims #292 after native parent-link verification.

## Worktree

- Worktree clean after implementation push.

**Status:** Child #291 implemented, verified, resolved, committed, and pushed; successor frontier recorded.
