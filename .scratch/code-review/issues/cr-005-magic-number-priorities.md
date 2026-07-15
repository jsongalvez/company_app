# CR-005: Magic number priorities

**Source:** Standards review of US-001–010 (chunk 1)

**What:** Priority values `10`, `20`, `100` are scattered as raw integer literals across services, repositories, and test helpers instead of named constants. Per `backend/AGENTS.md` "detekt MagicNumber is strict" — extract every literal into a `private const val`.

**Files:**
- `ReliefAccessService.grantAccess` — `priority = 10`
- `MedicalMissionDelegateRepository` — `priority = 20` (this one IS named `DELEGATE_PRIORITY` — but it's inconsistent)
- `DatabaseTestHelper` — `priority = 100` in `grantEditBranchData`, `grantManageProducts`

**Fix:** Define named constants in a shared location, e.g.:
```kotlin
const val RELIEF_ACCESS_PRIORITY = 10
const val DELEGATE_PRIORITY = 20
const val GRANT_PRIORITY = 100
```
Or better yet, a value type (see ADR-0003).

**Priority:** low (detekt doesn't flag inside `DatabaseTestHelper` since test source set config may differ)
**Story alignment:** US-009, US-010

**Status:** ✅ done
