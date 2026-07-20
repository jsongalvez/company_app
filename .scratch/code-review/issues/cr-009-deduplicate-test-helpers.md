# CR-009: Deduplicate test helpers across PostgresTest classes

**Source:** Standards review of US-001–010 (chunk 1)

**What:** `extractJsonField`, `insertUser`, `deleteTestRows` are duplicated (with slight variations) across every `*PostgresTest.kt` class. This is a classic **Duplicated Code** smell.

**Files:** `AttendanceServicePostgresTest`, `BranchServicePostgresTest`, `UserBranchAssignmentServicePostgresTest`, `UserServicePostgresTest`, and likely others.

**Fix:** Extract shared test helpers into `DatabaseTestHelper` or a shared test base class.

**Priority:** low (cosmetic, but reduces maintenance burden)
**Story alignment:** general
