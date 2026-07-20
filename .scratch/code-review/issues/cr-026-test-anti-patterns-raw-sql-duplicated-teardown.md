# CR-026: Test anti-patterns — raw SQL, CapabilityTable queries, duplicated teardown

**Source:** Chunks 2c (smells: "test helper queries CapabilityTable directly", "raw JDBC in tests"), 3 (smells: "raw SQL in tests"), 5 (smells: "duplicated test teardown")

**What:**
1. **Test helper queries CapabilityTable directly:** `DatabaseTestHelper.grantSubmitRemittance()` (and similar) queries `CapabilityTable` directly instead of using the service layer (`CapabilityService`). This means the test bypasses the auth path it's supposed to test.
2. **Raw JDBC/raw SQL in tests:** Several test files use `exec()` or raw SQL strings via `TransactionManager` instead of Exposed DSL. Violates `backend/AGENTS.md` "All database access MUST use the Exposed DSL."
3. **Duplicated test teardown:** The same setup/teardown block (delete from all tables) is copy-pasted across multiple test files.

**Files:**
- `backend/src/test/kotlin/com/companyb/companyapp/DatabaseTestHelper.kt`
- `backend/src/test/kotlin/com/companyb/companyapp/service/*PostgresTest.kt`

**Fix:**
1. Refactor `DatabaseTestHelper` to use service-layer methods for setup (e.g., use `UserManagementService` to assign roles, use `CapabilityService` to grant capabilities)
2. Replace raw SQL with Exposed DSL in all test files
3. Extract shared teardown/cleanup into a `DatabaseTestCleaner` helper or base test class

**Priority:** low
**Story alignment:** test infrastructure
