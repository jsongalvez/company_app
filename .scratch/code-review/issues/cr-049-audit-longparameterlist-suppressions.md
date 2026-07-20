# CR-049 — Audit remaining `@Suppress("LongParameterList")` in backend

**What to build:** Selectively reduce `LongParameterList` suppressions by extracting parameter data classes where the parameter group represents a cohesive domain concept. 45 instances remain in services and repositories (mostly create/update methods with many DB columns). Target only the clear data clumps — leave test helpers and isolated wide-parameter methods alone.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] 1. Identify data clumps across the 45 instances — parameters that travel together across multiple methods (e.g. session creation params: `clientId, branchId, isWalkIn, requestedPractitionerId`)
- [ ] 2. Prioritize top 5–8 worst offenders by parameter count (those with 8+ params)
- [ ] 3. Introduce parameter data classes for each prioritized group
- [ ] 4. Update service and repository callers to use the new data classes
- [ ] 5. Remove `@Suppress("LongParameterList")` where parameter count drops below 6
- [ ] 6. Run full quality gate (detekt, ktlintCheck, test, jmhClasses)
- [ ] 7. Leave test helpers (`DatabaseTestHelper.kt`, service tests) with existing suppressions
