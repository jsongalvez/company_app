# ADR-0017: Eliminate shallow engine pass-through layers

**Status:** Accepted  
**Date:** 2026-07-20  
**Driver:** Architecture deepening (#75) — candidate #3

## Context

Three `internal object` engines/helpers existed as thin pass-through layers:

- **CommissionEngine** — delegated from `CommissionService`; contained the actual recalculation logic and `splitCommission` arithmetic.
- **ManualInclusionHelper** — delegated from `CommissionService.createManualInclusion`; contained the upsert + audit + recalc orchestration.
- **SubmissionEngine** — validated DRAFT status + version match, then called `RemittanceRepository.submit`.

All three duplicated or fragmented logic that could live directly in their caller. `SubmissionEngine`'s pre-checks (status, version) were also duplicated inside `RemittanceRepository.submit()` — the engine performed non-locking reads before the repository did locking reads under `SERIALIZABLE` isolation, creating a TOCTOU gap and an inconsistent check order.

## Decision

1. **Inline CommissionEngine and ManualInclusionHelper into CommissionService.** `splitCommission`, `recalculate`, and `createManualInclusion` now live as methods on `CommissionService`. The public API surface (`getByBranchDayId`, `createManualInclusion`, `recalculate`, `manualRecalculate`) is unchanged. `splitCommission` remains public to support the JMH benchmark and `SplitCommissionAlgorithmTest`.
2. **Eliminate SubmissionEngine.** `RemittanceService.submit` calls `RemittanceRepository.submit` directly. The repository already performs all pre-checks (existence, version, DRAFT status) inside its `SERIALIZABLE` transaction. The `?: throw NotFoundException("Remittance not found")` for the null return case is handled in `RemittanceService`.
3. **Check order changes.** Inside `RemittanceRepository.submit`, version is checked before status (both inside `FOR UPDATE` + `SERIALIZABLE`). The old `SubmissionEngine` checked status before version using non-locking reads. This means: submitting with a wrong expectedVersion on any remittance (DRAFT or not) now yields `VersionMismatchException` (409) instead of potentially hitting the status check first.

## Consequences

- Three source files deleted, ~165 lines of code removed.
- Single source of truth for remittance submit pre-checks: `RemittanceRepository.submit` (inside the locking transaction).
- Edge case: previously, submitting an already-remitted remittance with a wrong version returned 400 (`ValidationException` for non-DRAFT). Now it returns 409 (`VersionMismatchException` for version mismatch). The old behavior was a concurrency anti-pattern — the pre-check used non-locking reads and could race. The 409 response is more informative (consumer knows the version was wrong, not just that the status is wrong).
- All 430 existing tests pass.
