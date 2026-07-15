# ADR-0003: Grant priority value type

**Status:** Proposed
**Date:** 2026-07-15

## Context

Priority values (`10` for relief grants, `20` for medical mission delegates, `100` for direct capability assignments) are currently passed as raw integers through the codebase and database. This leads to:
- Magic numbers without semantic meaning
- No type safety (any `Int` can be passed where a priority is expected)
- Inconsistency across modules (some define constants, others use literals)

## Decision

Introduce a value type (inline class) `GrantPriority` wrapping an `Int`:

```kotlin
@JvmInline
value class GrantPriority(val value: Int)
```

Define well-known constants alongside it:

```kotlin
object GrantPriorities {
    val RELIEF_ACCESS = GrantPriority(10)
    val MEDICAL_MISSION_DELEGATE = GrantPriority(20)
    val DIRECT_GRANT = GrantPriority(100)
}
```

The Exposed column mapping uses `customEnumeration` with the integer value serialized to a `SMALLINT` column.

## Consequences

- All priority-bearing functions now accept `GrantPriority` instead of `Int`/`Short`
- Eliminates magic numbers at call sites
- One place to adjust priority ordering if business rules change
- Migration needed for the DB column type (currently `SMALLINT`, stays compatible)
- A mechanical refactor across ~15 call sites in services, repositories, and tests
