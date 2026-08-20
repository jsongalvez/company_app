# Map #180 Candidate: Keep Soft-Deleted Expenses Immutable Under Races

## Question

Should an Expense update atomically require `deleted_at IS NULL` so a stale
update cannot mutate a soft-deleted financial record after a concurrent delete?

## Scope

Add the existing deleted-row predicate to the optimistic update transaction,
map a lost state transition to the established domain error, and add a
PostgreSQL interleaving regression test. Preserve live updates, restore,
version conflicts, and audit atomicity.

## Evidence

- `ExpenseService.kt:64-74` prechecks deletion before repository mutation.
- `ExpenseRepository.kt:147-160` updates by `(id, version)` without `deleted_at IS NULL`.
- `ExpenseRepository.kt:74-79` soft delete does not increment version.
- `ExpenseRepository.kt:102-110` already uses an atomic deleted-state predicate for restore.

## Verifier packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: OMEGA
- L1 fact integrity: pass
- L2 domain coherence: pass; soft-deleted financial records remain immutable
- L3 long-term architecture: pass; repository owns optimistic state classification
- L4 adversarial falsification: pass; update-read -> soft-delete -> update-write succeeds today
- L5 comprehension: pass
- deterministic gate: pass; source and restore precedent agree
- HARD findings: zero after `deleted_at IS NULL` update predicate
- SOFT findings: zero
- confidence: high
- artifact: Session 344 synthesis; `ExpenseRepository.kt:147-172`
