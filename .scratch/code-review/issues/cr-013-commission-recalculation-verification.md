# CR-013: Commission recalculation — verify all trigger points are wired

**Source:** Critical D — found in chunks 1, 2b (spec: "ProductSaleService.sell() logs 'stubbed'", "clock-in/out should trigger recalc")

**What:**
- Chunk 1 review found: `ProductSaleService.sell()` logged "stubbed" instead of calling `CommissionEngineService.recalculate()`
- Chunk 2b review found: clock-in/out did NOT trigger commission recalculation
- **Verification needed against HEAD:** Current code at `ProductSaleService.kt:111` calls `CommissionEngineService.recalculate(branchDayId)` — this may have been fixed in chunk 5 rebuild
- Current code also calls recalc from `AttendanceService.clockIn()`, `AttendanceService.clockOut()`, and `CommissionManualInclusionService.upsert()`

**Spec reference:** `docs/engine_specifications.md` Engine 1 Trigger Events — all four triggers required:
1. New `product_sale` inserted
2. `attendance` clock-in
3. `attendance` clock-out
4. `commission_manual_inclusion` added/changed

**Files (verify these):**
- `backend/src/main/kotlin/com/companyb/companyapp/service/ProductSaleService.kt`
- `backend/src/main/kotlin/com/companyb/companyapp/service/AttendanceService.kt`
- `backend/src/main/kotlin/com/companyb/companyapp/service/CommissionManualInclusionService.kt`

**Fix:** Verify all 4 trigger points call `CommissionEngineService.recalculate()` at HEAD. If any are missing:
1. Add `CommissionEngineService.recalculate(branchDayId)` at the end of each method, inside the transaction
2. Check that PAST/REMITTED guard inside `recalculate()` is working (should abort unless manual re-run)

**Priority:** medium (may already be resolved — verify first)
**Story alignment:** US-006 (Clock-in), US-007 (Clock-out), US-024 (Commission Recalculation)
