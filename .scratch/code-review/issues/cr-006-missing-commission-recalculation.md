# CR-006: Missing commission recalculation on clock-in/out

**Source:** Spec review of US-001–010 (chunk 1)

**What:** Both US-006 and US-007 require "Trigger commission recalculation for the branch day" in their requirements. Neither `AttendanceService.clockIn` nor `AttendanceService.clockOut` calls any commission recalculation.

**Spec references:**
- US-006 requirement: "Trigger commission recalculation for the branch day"
- US-007 requirement: "Trigger commission recalculation for the branch day"

**Fix:** Call the commission recalculation service (e.g., `CommissionService.recalculateForBranchDay`) at the end of both `clockIn` and `clockOut`, inside the same transaction.

**Priority:** medium
**Blocked by:** Commission service implementation (US-024)
**Story alignment:** US-006, US-007

**Status:** ✅ done
