# ADR-0016: Typed VersionMismatchException for optimistic-lock conflicts

**Status:** Accepted
**Date:** 2026-07-20

## Context

Optimistic-lock conflicts were signaled by throwing `error("version_mismatch")` — a raw Kotlin `IllegalStateException` carrying a magic-string message. Services caught the `IllegalStateException` and pattern-matched on `e.message == "version_mismatch"` to re-throw a `ConflictException`. This pattern existed across 9 repository call sites and 6 service catch blocks.

The string-matching pattern had three drawbacks:
1. **No compile-time safety** — a typo in the magic string ("versoin_mismatch") would silently pass through the catch block and crash with a 500 instead of a 409.
2. **No structured context** — the exception carried no table name or record ID, making debugging harder.
3. **Scattered error handling** — version-conflict logic lived in two layers (repository throws, service catches/re-throws), even though the centralized handler already mapped `ConflictException` → 409.

## Decision

Introduce `VersionMismatchException(table: String, recordId: UUID)` extending `ConflictException` in the domain exception hierarchy. Repositories throw this directly. The exception propagates through the service layer to the centralized handler, which already maps `ConflictException` → 409.

## Consequences

**Positive:**
- Compile-time safety — cannot typo the exception type.
- Structured context — every version-mismatch error carries the table name and record ID in its message.
- No dead catch blocks — 6 service try/catch blocks were deleted.
- Consistent pattern — all optimistic-lock violations now use the same exception type.

**Negative:**
- None identified.

## Implementation notes

- `VersionMismatchException` extends `ConflictException`, not `RuntimeException` directly. This lets it reuse the existing 409 mapping in `Main.kt`.
- The `table` parameter accepts the Exposed Table's `.tableName` string.
- Pre-read version checks in services (e.g., `SessionService.updateStatus` checking `session.version != expectedVersion` before calling the repository) remain as direct `ConflictException` throws — they are not repository optimistic-lock errors.
- Single-call-site violations (`insufficient_stock`, `not_draft`) were also converted from `error("string")` to direct `ValidationException` throws as part of the same cleanup.
