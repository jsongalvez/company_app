# ADR-0024: Command-owned mutation transactions and audit boundaries

**Status:** Accepted
**Date:** 2026-08-21
**Amends:** [ADR-0013](./0013-audit-callback-pattern.md) (audit-callback pattern becomes transitional), [ADR-0019](./0019-repository-owns-before-state-capture.md) (invariant preserved; ownership moves to the command)
**Ticket:** [#319](https://github.com/jsongalvez/company_app/issues/319) (map #317 Foundation B)
**Proof path:** Expense feature (commit `ref #319`, tests `ExpenseServicePostgresTest` §#319 contract proofs + `ExpenseCommandOwnershipArchitectureTest`)

## Context

Mutation transaction ownership is inconsistent. Most services delegate the whole mutation — including business workflow — to repositories that open their own `transaction {}`; services inject audit writes through `auditFn` callbacks so they execute inside those repository-owned transactions (ADR-0013). Several services (Attendance, ProductSale, Commission, UserBranchAssignment) own transactions whose repository calls open further transactions. `RemittanceRepository.submit/undo` are SERIALIZABLE multi-table workflows living in a repository. The topology works atomically only because Exposed joins nested blocks onto the ambient transaction; ownership is invisible at every seam.

ADR-0013 documented this as a scoped migration compromise, not an end state. Map #317 Foundation B (#319) required one proven ownership contract before the broad migrations (#320 Remittance, #321 Attendance, #323 retirement sweep).

## Decision

**A public feature command owns its business transaction; persistence is an in-transaction store operation; the audit row is written directly into that same transaction.**

### The contract

1. **One command, one transaction.** Each public mutating service method opens exactly one `transaction {}` and chooses its isolation there (e.g. `Connection.TRANSACTION_SERIALIZABLE` for remittance submit/undo when migrated). Routes never open transactions.
2. **Repositories are internal stores.** Mutating repository functions are named `*InTransaction`: they open no transaction and accept no `auditFn`. They run on the caller's transaction and keep their row locks (`forUpdate()`), optimistic-version predicates, idempotency guards, and atomic check+write WHERE clauses. Calling one outside any transaction fails loudly (Exposed throws).
3. **Audit is a direct call.** The command calls `AuditLogRepository.record*` itself after the write, inside its own transaction. No callback parameter, no coordination. Audit commits with the mutation or not at all. A collaborating semantic owner reached through its feature boundary (#603: Branch Day day-transitions called from the remittance submit/undo transaction) writes its own domain audit on that same open transaction — the top-level command still owns the transaction, and the collaborator opens none. The caller supplies only actor/reason and never carries the collaborator's before/after images.
4. **Ordering inside the command:** lock/read before-state → domain validation → day-state gate → write → capture after-state → insert audit row.
5. **Before/after stays transaction-local and serialized (ADR-0019 preserved, #522).** The command reads the before entity through a serializing read *inside its own transaction* — entity-row lock (`acquireLockInTransaction` / `findByIdForUpdateInTransaction`), a parent/day lock covering the row, or a validated version CAS — rather than across a method boundary, then passes both snapshots to the audit call. Transaction locality alone is not enough: a non-locking read inside the transaction still records a stale predecessor when a concurrent writer commits between the read and the write.
6. **Reads keep wrappers.** Read helpers may retain convenient `transaction {}` forms (`findById`, list queries); only mutating operations follow rule 2.

What this deliberately does **not** create: no `UnitOfWork`, no transaction-manager framework, no interface hierarchy, no generic `Repository<T>`. The pattern is three conventions (suffix naming, no-tx mutators, direct audit calls) enforced by visibility discipline today and by automated checks from #324 onward.

### Failure semantics

Any failure inside the command — validation, constraint violation, or a failing audit insert — rolls back the whole transaction: no domain row without its audit row, no audit row for a failed mutation. Proven by failure-injection tests on the Expense proof path:

| Invariant | Test |
|---|---|
| Domain write + audit commit together | `command commits domain write and audit atomically` |
| Audit failure rolls the mutation back | `audit failure inside the command rolls the mutation back` |
| Mutation failure creates no audit row | `mutation failure writes no audit row` |
| Before/after transaction-local | before-read moved inside command transaction (`update`/`softDelete`/`restore`) |
| No nested write transaction remains | `ExpenseCommandOwnershipArchitectureTest` source scan |

## Migration inventory (grouped by feature)

State at contract acceptance. "Command-owned" = service opens tx; "repo-owned" = repository opens the mutation's tx; "callback" = `auditFn` present.

| Feature | Current shape | Migration notes |
|---|---|---|
| **Expense** | ✅ Command-owned (this ticket) | Reference implementation of rules 1–6 |
| **Remittance** | Repo-owned SERIALIZABLE workflows (`submit`, `undo`), repo-owned line/snapshot/breakdown CRUD, callbacks throughout | Highest-value target (#320): move `submit`/`undo` bodies into `RemittanceService` commands; snapshot/breakdown stores become `*InTransaction`; preserve SERIALIZABLE + row locks + version predicates exactly |
| **ProductSale / Inventory** | Service tx exists but nests repo txs; `ProductSaleRepository.sell` is a workflow repo (lock + stock guard + movement insert + compound audit) | Second reference-grade migration: lift `sell` composition into `ProductSaleService.sell` command; inventory card lock/version stay in store fns |
| **Attendance** | Service txs nest repo txs (+ `CommissionService.recalculate` joins transitively); callbacks in `clockIn`/`clockOut` | Migrate with #321; must preserve commission-trigger atomicity and idempotent clock-in/out semantics |
| **Session / practitioners / void / base rate** | Fully repo-owned, optimistic versions + client locks in repos, callbacks | Straightforward mechanical migration during #323; guards stay in store fns |
| **Commission** | Service-owned recalc/manual-inclusion nesting repo txs | Fold inner repo txs into the service command; split-replace stays one store fn |
| **User / assignments / relief / invites** | Repo-owned with callbacks; `findActive(forUpdate)` lock helper | Mechanical; keep windowed-grant atomicity (`grantWithCapability`) as store-level check+write |
| **Client / product / branch / category / compensation / allowance / concern / notification / delegates** | Simple repo-owned CRUD + callbacks | Bulk-mechanical during #323 |
| **BranchDay** | Repo-owned lazy-create/status transitions; `acquireLock` helper | Store ops under `BranchDayService` commands; day resolution already centralized (#318) |

### Explicit exceptions

- **`CompensationRoutes` middleware** reads `CompensationRepository.findById` directly for capability scoping — route-layer read, no mutation; leave until #324 visibility work.
- **`NextAppointmentScheduler`** reads in service txs and batch-inserts notifications — scheduler-owned batch write with no per-row audit requirement; migrate to the same shape opportunistically, no urgency.
- **Read helpers everywhere** keep their convenience wrappers (rule 6).

## Consequences

- **Positive:** One obvious owner per mutation; audit atomicity structural instead of coordinated; the deletion test has a concrete target — #320/#321/#323 remove `auditFn` parameters (~50 methods) and nested write blocks by applying this shape.
- **Positive:** Before-state staleness class (the reason ADR-0019 existed) disappears structurally: nothing crosses a transaction boundary between read and write, and the before-read serializes with competing writers per rule 5.
- **Negative (transitional):** Un-migrated repositories still open their own txs; called from a migrated command they join it silently. Acceptable only while #320–#323 complete the sweep; the architecture test pins each migrated module so the mixed state cannot regrow inside finished modules.
- **Negative:** Misuse (calling a `*InTransaction` store fn outside a command) fails at runtime, not compile time, until #324 adds static enforcement.
