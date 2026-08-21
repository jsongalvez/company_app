# ADR-0019: Repository owns before-state capture for entity-based audit diffs

**Status:** Accepted  
**Date:** 2026-07-20  
**Supersedes:** Amends ADR-0013 (service-owns-before-state) for entity-based overloads  
**Amended by:** [ADR-0024](./0024-command-owned-mutation-transactions.md) (2026-08-21) — the **invariant stands**: before/after must be read inside the mutation's transaction. The **owner moved**: with command-owned mutations the command captures before state via `findByIdInTransaction` inside its own transaction; repositories are `*InTransaction` store operations that open no transaction. The repository-owned capture described below applies only to the retired callback-era code paths kept as history.

## Context

ADR-0013 prescribed that the service fetches the "before" entity prior to calling the repository, then passes it to the audit lambda along with the "after" entity from the callback. This worked for the map-based `recordUpdate(oldFields, newFields)` pattern where the service could build field maps from the before entity.

With ADR-0018's entity-based `recordUpdate<T>(before, after, auditFields)` overload, the service needs both entities at the call site. The service-fetches-before approach forces the service to open a separate `findById()` query outside the repository's `transaction{}`, or to capture the result of a preceding `findById()` and hold it across method boundaries. Both are fragile: the before entity can become stale between the read and the mutation.

## Decision

For entity-based audit overloads, the repository captures the "before" entity inside its own `transaction{}` block, performs the mutation, captures the "after" entity, and passes both to the `auditFn` callback as `(T, T) -> Unit`. The service no longer fetches the before state.

This applies to: `UserBranchAssignmentRepository.setEndedAt`/`updateSlot`, `ReliefAccessRepository.grantWithCapability`/`deny`, `RemittanceLineRepository.softDeleteLine`, `AttendanceRepository.clockOut`, `RemittanceRepository.submit` (via `SubmitAuditContext`), and `ProductSaleRepository.sell` (pre-existing pattern).

The map-based `recordUpdate(oldFields, newFields)` overload remains unchanged — services using it may still fetch before state per ADR-0013.

## Considered Options

1. **Service fetches before (ADR-0013 status quo).** Service calls `findById()`, holds the entity, calls the repository, then uses both entities in the lambda. Problem: the before entity can diverge from the state actually seen inside the repository's transaction (concurrent modification between the service read and the repository write).

2. **Repository passes both (chosen).** Single `transaction{}` block guarantees before and after are read from the same transaction. Negligible overhead (one extra `selectAll().single()` per mutation).

## Consequences

- **Positive:** Before/after atomicity guaranteed — both reads happen inside the same transaction.
- **Positive:** Service code is simpler — no separate `findById()` + variable capture.
- **Negative:** ADR-0013's service-owns-before statement is now incorrect for entity-based paths. This ADR amends it.
- **Negative:** Slight code duplication — 6 repositories independently implement the same before-read pattern. Acceptable given the repositories have different entity types and mutation logic.
