# ADR 0015: Test helpers own Exposed DSL instead of delegating to production repositories

**Date:** 2026-07-19  
**Status:** ✅ accepted  
**Stakeholders:** backend team

## Context

`CapabilityRepository` had two mutation methods (`grantCapability`, `revokeAllCapabilities`) and a `GrantCapabilityParams` data class that were only ever called from `DatabaseTestHelper` — never from any production service. This created a "two reasons to change" code smell: the repository changed when query logic changed AND when test data setup changed.

These methods were added in CR-026 (commit `ffc6d1b`) as part of fixing "test helper queries CapabilityTable directly." The fix introduced production API surface for a test-only concern.

## Decision

**Remove the mutation methods from `CapabilityRepository` and inline direct Exposed DSL into `DatabaseTestHelper`.**

`CapabilityRepository.grantCapability()` → `DatabaseTestHelper` calls `UserCapabilityTable.insert {}` directly.  
`CapabilityRepository.revokeAllCapabilities()` → `DatabaseTestHelper` calls `UserCapabilityTable.deleteWhere {}` directly.  
`CapabilityRepository` is now read-only: 3 query methods (`findIdByCode`, `hasCapability`, `findCapabilitiesForUser`).

## Considered options

1. **Delegation (the CR-026 approach, rejected):** Keep adding repository methods for every test-only use case. Rejected because it puts test concerns in production code and makes the repository change for two unrelated reasons.

2. **Service-layer delegation (#13's original suggestion):** Route test data setup through `CapabilityService`/`UserManagementService`. Rejected because services may enforce business rules (e.g., role→capability seeding) that interfere with precise test setup.

3. **Own DSL in test helper (chosen):** `DatabaseTestHelper` directly uses Exposed DSL — the same pattern used by the other 22 `insertTest*` methods in the same file.

## Consequences

- `CapabilityRepository` is now read-only, consistent with `CapabilityService`.
- Test data setup is co-located in the test helper, visible without cross-referencing production code.
- `GrantCapabilityParams` is eliminated — the flat parameter list on `DatabaseTestHelper.grantCapability()` uses the existing `@Suppress("LongParameterList")` convention.
- Issues #56 (CR-033) and the capability-related parts of #13 (CR-026) are superseded.
