# ADR 0014: Table-hosted audit field mapping

**Date:** 2026-07-18  
**Status:** ✅ accepted (amended 2026-07-19)  
**Stakeholders:** backend team

## Context

ADR 0013's callback pattern left ~40 duplicated `AuditLogRepository.record(...)` blocks across
services. When a developer added a column to a table, they had to remember to update the audit
field mapping in a separate file. A pilot run of 17 per-table helper files under `service/audit/`
was rejected: it scattered mappings away from column definitions.

An initial approach used an `Auditable` interface with `toAuditFields()` on entity data classes,
co-located in the same file as the Table. This eliminated duplication but created a maintenance
double-declaration problem: the data class declared audit fields, but the Table declared columns.
Adding a column required updating two things in the same file, with no compiler enforcement.

## Decision

Each Table companion defines a `fun auditFields(entity): Map<String, String>` function — the
Table is the single authority for what gets audited. When a developer adds a column, the audit
mapping is an explicit function on the same `object`, impossible to overlook.

Convenience methods on `AuditLogRepository`:

- `recordInsert(tableName, recordId, changedBy, fields)` — takes a `Map<String, String>`
  provided by the call site
- `recordUpdate(tableName, recordId, oldFields, newFields, changedBy)` — unchanged
- `recordDelete(tableName, recordId, oldFields, newFields, changedBy, reason?)` — unchanged

Service call sites explicitly name the Table that owns the audit definition:
```kotlin
AuditLogRepository.recordInsert(
    tableName = ClientTable.tableName,
    recordId = client.id,
    changedBy = callerId,
    fields = ClientTable.auditFields(client),
)
```

Nullable entity fields are encoded as JSON null (#525; historical rows use the
legacy literal string `"null"` and readers accept both).

## Rejected: Auditable interface on entities

The initial ADR 0014 put `toAuditFields()` on entity data classes via an `Auditable` interface.
This was rejected because:

- Data classes are value carriers, not schema authorities — the Table owns column definitions
- Field mapping on the entity duplicates the Table's column list without compiler enforcement
- The required field-parity tests were compensating for a design that put the mapping in the wrong
  home

## Consequences

- **Positive:** Single source of truth — the Table companion. Adding a column and forgetting
  `auditFields()` means the audit function won't compile at that entity's call sites, not just
  a failing test.
- **Positive:** Zero additional files; `Auditable.kt` deleted.
- **Positive:** Call sites remain concise (1 line) while making the audit definition's location
  explicit (`ClientTable.auditFields(client)`).
- **Negative (accepted):** `auditFields()` is still a manual map — developers must add entries
  for new columns. The compiler catches missing invocations but not missing entries within the map.
