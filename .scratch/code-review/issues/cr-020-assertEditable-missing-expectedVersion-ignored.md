# CR-020: assertEditable not called + expectedVersion ignored in update paths

**Source:** Chunk 2b (spec: "assertEditable not called", "expectedVersion ignored")

**What:**
1. **assertEditable not called:** Some mutating operations that write to branch-day-scoped tables (sessions, expenses, etc.) do not call `BranchDayService.assertEditable(branchDayId, userId)` before performing the write. This bypasses day-state enforcement (OPEN/PAST/REMITTED) and flagged audit enforcement for REMITTED days.
2. **expectedVersion ignored:** At least one update path passes `expectedVersion` but the repository ignores it (`UPDATE ... WHERE id = :id` without `AND version = :expectedVersion`). This defeats optimistic locking — concurrent edits will silently overwrite each other.

**Files:** (investigate all update paths)
- `backend/.../service/ExpenseService.kt`
- `backend/.../service/AllowanceService.kt`
- `backend/.../service/CompensationService.kt`
- `backend/.../service/ProductSaleService.kt`

**Fix:**
1. Audit all mutating service methods that touch branch-day-scoped tables — ensure `assertEditable` is called before every write
2. Audit all UPDATE statements — ensure every one that receives `expectedVersion` includes `AND version = :expectedVersion` in the WHERE clause
3. Add integration tests that assert 409 on version mismatch

**Priority:** high
**Story alignment:** cross-cutting — all mutating endpoints on OPEN/PAST/REMITTED days
