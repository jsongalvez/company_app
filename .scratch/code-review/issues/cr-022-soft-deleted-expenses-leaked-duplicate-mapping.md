# CR-022: GET expenses returns soft-deleted + duplicate utility patterns

**Source:** Chunk 2b (spec: "GET expenses returns soft-deleted"), Chunk 3 (smells: "duplicate summary mapping", "duplicate export query body")

**What:**
1. **Soft-deleted expenses leaked:** `GET /api/expenses?branchDayId=...` returns expenses where `deleted_at IS NOT NULL`. The query should filter `WHERE deleted_at IS NULL` (or have a dedicated endpoint for viewing deleted records).
2. **Duplicate summary mapping:** Summary/report response mapping logic is duplicated across multiple endpoints (daily, monthly, all-time exports). Should be extracted to a shared mapper.
3. **Duplicate export query body:** Export endpoint query construction is copied across export types.

**Files:**
- `backend/.../repository/ExpenseRepository.kt` — `findByBranchDayId`
- `backend/.../api/mapping/` — response mappers
- `backend/.../api/routes/ExportRoutes.kt`

**Fix:**
1. Add `AND deleted_at IS NULL` filter to expense read queries (or make deleted-filtered the default, with a `?includeDeleted=true` param)
2. Extract shared `SummaryMapper` / `ExportQueryBuilder` to reduce duplication
3. Write integration test that creates + soft-deletes an expense, then verifies GET excludes it

**Priority:** medium
**Story alignment:** US-022 (Expenses), US-034 (Exports)

**Status:** ✅ done
