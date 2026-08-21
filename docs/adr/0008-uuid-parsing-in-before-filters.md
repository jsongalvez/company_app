# ADR-0008: UUID parsing helpers in `RoutesUtil.kt`

**Status:** Accepted
**Date:** 2026-07-17
**Updated:** 2026-07-17 (added `callerUuid()`)

## Context

Route handlers and before-filters frequently need to parse UUIDs from four sources:
path parameters, query parameters, request body fields, and the authenticated user ID
(context attribute). Each inline `UUID.fromString()` call is a null-safety hazard and a
duplication of error-handling logic.

The original issue (ADR-0008 v1) targeted `runCatching { UUID.fromString(...) }` blocks
in `ExpenseRoutes.kt`. The `callerUuid()` helper was added later when a security review
(CR-031) found 60+ call sites passing a nullable `context.attribute<String>("userId")`
directly to `UUID.fromString()` without a null guard.

## Decision

1. **Do not** use `runCatching { UUID.fromString(...) }.getOrElse { throw BadRequestResponse(...) }`
   in before-filters or route handlers.

2. Use `context.pathParamAsUuid(name)` for path parameters.

3. For body/query UUIDs, use `uuidOrThrow(value, name)`, `uuidFromQuery(name)`, or
   `uuidFromBody(key)` from `RoutesUtil.kt`.

4. Use `context.callerUuid()` to extract the authenticated caller UUID from the `"userId"`
   context attribute. This replaces the previous pattern of
   `UUID.fromString(context.attribute<String>("userId"))` which could throw NPE if the
   attribute was absent.

5. The helpers return distinct error messages:
   - `callerUuid()`: `"Missing authentication"` (attribute absent) vs `"Invalid user ID in authentication"` (malformed UUID)
   - `pathParamAsUuid()`: `"Invalid <name>"`
   - `uuidFromQuery()`: `"<name> query param is required"` (absent) vs `"Invalid <name>"` (malformed)

## Rationale

Centralising UUID parsing into helpers eliminates the null-safety gap at every call site
and makes error messages consistent. The `callerUuid()` helper specifically was motivated
by the auth attribute being typed as `String?` in Javalin's API — the helper is the single
place where the null check and UUID validation happen.

## Refactor scope

The anti-pattern (inline `runCatching`/`UUID.fromString`) appeared in:

- `ExpenseRoutes.kt:27-28` (body `branchDayId`)
- `ExpenseRoutes.kt:35-36` (query param `branchDayId`)
- Route handler bodies in `ExpenseRoutes.kt:60-62`, `105-107`

The `UUID.fromString(context.attribute<String>("userId"))` anti-pattern appeared in all
24 route/filter files (~60 occurrences), all migrated to `context.callerUuid()`.

See the original refactor commit history for details.
