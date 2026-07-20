# CR-044: Extract grantCapability delegation in DatabaseTestHelper

**Source:** Code review of CR-009 (scope creep finding)

**What:** The existing `grantManageUsers`, `grantEditBranchData`, `grantManageProducts`, `grantVoidSession`, `grantAssignCompensation`, `grantAssignDelegate`, and `grantSubmitRemittance` methods in `DatabaseTestHelper` each contain duplicated transaction blocks that query `CapabilityTable` and insert into `UserCapabilityTable`. Extract them to delegate to a shared private `grantCapability()` helper, matching the pattern already used by the new helpers in CR-009.

**Files:**
- `backend/src/test/kotlin/com/companyb/companyapp/test/DatabaseTestHelper.kt`

**Fix:**
1. Add private `grantCapability(userId, capabilityCode, contextType, contextId, sourceId, priority)` helper
2. Rewrite each `grant*` method as a one-liner delegation to `grantCapability()`

**Net impact:** ~-70 lines in `DatabaseTestHelper.kt`

**Priority:** low
**Story alignment:** test infrastructure

**Status:** ✅ done
