# ADR 0013: Audit callback pattern for repository-to-service migration

**Date:** 2026-07-18  
**Status:** ✅ accepted (amended 2026-07-20 by [ADR-0019](./0019-repository-owns-before-state-capture.md))  
**Stakeholders:** backend team

## Context

Per `docs/architecture.md` §12.1, audit entries must be written in the service layer
only — never in routes, never in repositories. However, the existing codebase had
`AuditLogRepository.record()` calls inside 24 repository files (49 call sites), each inside the
repository's own `transaction{}` block.

Moving audit to the service layer creates a tension: `AuditLogRepository.record()` opens its own
`transaction{}` which **joins** an enclosing transaction. If the service calls `record()` after the
repository method returns, the enclosing transaction has already committed — the audit runs in a
separate transaction, breaking atomicity.

## Decision

**Use a callback pattern:** repository mutating methods accept an `auditFn` parameter (defaulting to
`{}`) and invoke it inside their `transaction{}` block. The service provides the lambda, which calls
`AuditLogRepository.record()`.

```kotlin
// Repository
fun addLine(params: AddLineParams, auditFn: (RemittanceLine) -> Unit = {}): RemittanceLine =
    transaction {
        // ... mutation ...
        auditFn(created)
        created
    }

// Service
RemittanceLineRepository.addLine(params) { line ->
    AuditLogRepository.record(
        tableName = RemittanceLineTable.tableName,
        recordId = line.id,
        action = AuditAction.INSERT,
        changedBy = callerId,
        newValue = AuditLogRepository.jsonFields(...),
    )
}
```

**For complex multi-audit operations** (e.g., `ProductSale.sell` writes 2 audit records, `BranchInventory.restock` writes 2), we pass a context data class through the callback:

```kotlin
data class SaleAuditData(
    val sale: ProductSale,
    val inventoryCardId: UUID,
    val oldStock: Int, val oldVersion: Int,
    val newStock: Int, val newVersion: Int,
)

fun sell(params: SellProductParams, auditFn: (SaleAuditData) -> Unit = {}): ProductSale
```

**For UPDATE audits needing before/after diffs**, the service fetches the "before" state prior to
calling the repository, then the callback provides the "after" state:

```kotlin
val before = ExpenseRepository.findById(expenseId) ?: throw NotFoundException(...)
ExpenseRepository.softDelete(expenseId, callerId, reason) { after ->
    AuditLogRepository.record(
        oldValue = AuditLogRepository.jsonFields("amount" to before.amount.toPlainString(), ...),
        newValue = AuditLogRepository.jsonFields("amount" to after.amount.toPlainString(), ...),
        ...
    )
}
```

> **Note (2026-07-20, entity-based overloads):** With the entity-based `recordUpdate<T>` / `recordDelete<T>` pattern (see [ADR-0018](./0018-entity-audit-overload-lambda-pattern.md)), before-state capture moved from the service to the repository. The repository reads the "before" entity inside its `transaction{}`, mutates, reads the "after" entity, and passes `(before, after)` to the `auditFn` callback as `(T, T) -> Unit`. This guarantees both reads are atomic within the same transaction. See [ADR-0019](./0019-repository-owns-before-state-capture.md) for the full rationale. The service-only before-fetch described above still applies to map-based `recordUpdate(oldFields, newFields)` call sites.

### Parameters removed from repositories

Parameters that existed solely to feed `AuditLogRepository.record()` (`changedBy`, `callerId`,
`reason`, `createdBy`) were removed from repository method signatures since the service now handles
audit. The service already has these values from its own parameters.

## Alternatives considered

1. **Service opens `transaction{}`** — Requires removing `transaction{}` from all repository
   methods. More invasive: every repo method signature and call site changes. Rejected for scope.

2. **Repo returns audit instructions** — Repository returns a lazy audit descriptor for the service
   to execute. Over-abstracted for a single concern. Rejected.

3. **Keep audit in repos (status quo)** — Violates G1. Rejected.

## Consequences

- **Positive:** G1 satisfied. Audit logic lives alongside business logic in the service layer.
  Atomicity preserved — audit runs inside the same DB transaction as the mutation.
- **Positive (2026-07):** Inner `transaction {}` removed from `AuditLogRepository.record()`
  (epic [#49](https://github.com/jsongalvez/company_app/issues/49)). It now inserts on the caller's
  connection, eliminating unnecessary savepoint overhead. Callers must ensure they're inside a
  transaction.
- **Positive (2026-07):** The ~40 duplicated `AuditLogRepository.record(...)` blocks were
  eliminated via `recordInsert`/`recordUpdate`/`recordDelete` convenience methods and the
  Table-hosted `auditFields()` pattern (see [ADR 0014](./0014-entity-audit-field-mapping.md)).
- **Negative:** Services must import table objects (`XxxTable`) to reference `tableName` in audit
  calls — a minor layer violation.
