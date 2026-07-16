# ADR-0008: UUID parsing in route before-filters must use `pathParamAsUuid`

**Status:** Accepted
**Date:** 2026-07-17

## Context

`ExpenseRoutes.kt` (introduced in ADR-0007) uses inline
`runCatching { UUID.fromString(...) }.getOrElse { throw BadRequestResponse(...) }` blocks
to parse UUIDs from request bodies and query parameters inside `config.routes.before` filters:

```kotlin
// ExpenseRoutes.kt:27-28 — anti-pattern
runCatching { UUID.fromString(request.branchDayId) }
    .getOrElse { throw BadRequestResponse("Invalid branch day id") }
```

The project already has `RoutesUtil.pathParamAsUuid` which encapsulates this pattern for
path parameters. Body and query parameters lack an equivalent utility, but the raw
`runCatching`/`UUID.fromString`/`getOrElse` triplet duplicates logic across every filter
that needs to parse a UUID and makes it easy to get the error message format wrong.

## Decision

1. **Do not** use `runCatching { UUID.fromString(...) }.getOrElse { throw BadRequestResponse(...) }`
   in before-filters or route handlers.

2. Use `context.pathParamAsUuid(name)` for path parameters (already available via
   `RoutesUtil.kt` extension).

3. For body/query UUIDs, use a consistent helper (either an extension on `Context` or a
   top-level function in `RoutesUtil.kt`) to keep the pattern DRY.

## Refactor scope

The anti-pattern currently appears in:

- `ExpenseRoutes.kt:27-28` (body `branchDayId`)
- `ExpenseRoutes.kt:35-36` (query param `branchDayId`)
- The route handler bodies in `ExpenseRoutes.kt:60-62`, `105-107` (duplicated logic in
  handlers after the filter)

See [#4](../../.scratch/issues/0004-refactor-uuid-parsing/ISSUE.md) for the tracked
refactor work.
