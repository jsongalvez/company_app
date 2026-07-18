# ADR 0014: Entity-centered audit field mapping via Auditable interface

**Date:** 2026-07-18  
**Status:** ✅ accepted  
**Stakeholders:** backend team

## Context

ADR 0013's callback pattern left ~40 duplicated `AuditLogRepository.record(...)` blocks across
services, each manually constructing a `jsonFields()` call. When a developer added a column to a
table, they had to remember to update the audit field mapping in a separate file — no canonical
location for "what fields get audited for this entity."

A pilot run of 17 per-table helper files under `service/audit/` was rejected: it scattered field
mappings away from their entity definitions, and the helpers were shallow wrappers.

## Decision

Each audited domain entity implements an `Auditable` interface with `toAuditFields(): Map<String,
String>`, co-located in the same file as the entity data class and its Exposed Table object.
`AuditLogRepository` exposes three convenience methods that consume the interface:

- `recordInsert(tableName, entity, changedBy)` — uses `entity.toAuditFields()`
- `recordUpdate(tableName, recordId, oldFields, newFields, changedBy)` — takes explicit `Map`
  for delta-only audits
- `recordDelete(tableName, recordId, oldFields, newFields, changedBy, reason?)`

For UPDATE/DELETE audits that track only a subset of fields, the call site passes explicit
`Map<String, String>` values. For INSERT audits, `recordInsert` delegates to `toAuditFields()`.

Nullable entity fields are encoded as the literal string `"null"` — not empty string or omission
— for consistent audit JSON shape.

## Alternatives considered

1. **Per-table helper files (pilot)** — 17 files under `service/audit/`. Rejected: worse locality
   (field mapping away from column definition), shallow wrappers.
2. **Auto-generate from Table column metadata** — Reflection-based approach. Rejected: not all
   columns need to be audited, and the selected subset is a deliberate choice.
3. **Keep manual `jsonFields()` in every service** — Status quo. Rejected: 15-line boilerplate
   per call site.

## Consequences

- **Positive:** Field mapping lives next to column definitions — impossible to miss when adding
  a column.
- **Positive:** Service call sites reduced from ~15 lines to 1 line for INSERTs.
- **Positive:** Zero new files.
- **Negative (accepted):** `toAuditFields()` is a manual maintenance point — adding a column
  requires a corresponding entry in the map. Field-parity tests in
  `AuditLogRepositoryTest` mitigate by asserting expected key sets for each entity.
