# CR-027: N+1 query in scheduler + notification table issues

**Source:** Chunks 2c (smell: "N+1 scheduler", spec: "notification table missing message"), 3 (spec: "scheduler notifies MANAGERs too")

**What:**
1. **N+1 query:** `NextAppointmentScheduler.run()` queries sessions, then for each session separately queries coordinators (one DB round-trip per session). Should be batched: fetch all sessions, then fetch coordinators for all affected branches in one query.
2. **Notification table missing `message` column:** The `notification` table per spec should have `message TEXT NOT NULL`. The current schema may be missing this column.
3. **Scheduler notifies MANAGERs too:** `findActiveCoordinatorsForBranch()` includes MANAGER role. Per business requirements, only Coordinators should receive next-appointment alerts.

**Spec reference:** `docs/design_specification.md` §20: notification table schema; `docs/architecture_implementation_plan.md` §C5

**Files:**
- `backend/.../service/NextAppointmentScheduler.kt`
- `V1__full_schema.sql` — `notification` table definition (fix via V5 migration, see CR-010)

**Fix:**
1. Batch coordinator fetch: collect all distinct branch IDs from the session results, then query coordinators for all branches in one `WHERE branchId IN (...)` query
2. Add `message` column to notification table via migration (see CR-010)
3. Fix coordinator filter: only COORDINATOR role, not MANAGER (or filter by `VIEW_BRANCH_DATA` capability — see CR-012)

**Priority:** medium
**Story alignment:** US-029 (Next Appointment Alerts)
