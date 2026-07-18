# ADR 0013: Audit callback pattern for repository-to-service migration

**Date:** 2026-07-18  
**Status:** ✅ accepted  
**Stakeholders:** backend team

## Context

Per `docs/architecture_implementation_plan.md` §G1, audit entries must be written in the service layer
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
- **Negative:** ~40 duplicated `AuditLogRepository.record(...)` blocks across services.
  Follow-up issue [#46](https://github.com/jsongalvez/company_app/issues/46) tracks extracting
  shared audit factories.
- **Negative:** Services must import table objects (`XxxTable`) to reference `tableName` in audit
  calls — a minor layer violation.
