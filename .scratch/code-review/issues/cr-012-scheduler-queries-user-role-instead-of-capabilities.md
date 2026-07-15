# CR-012: Scheduler queries user_role at runtime — violates capability-only auth (A1)

**Source:** Critical C — found in chunks 2c, 3 (standards + spec: "Scheduler queries roles", "Scheduler notifies MANAGERs too")

**What:**
- `NextAppointmentScheduler.findActiveCoordinatorsForBranch()` performs two-step query:
  1. `SELECT id FROM role WHERE name IN ('COORDINATOR', 'MANAGER')`
  2. `UserBranchAssignmentTable INNER JOIN UserRoleTable ... WHERE roleId IN coordinatorRoleIds`
- This violates A1: "All permission checks go through `active_user_capabilities` view, never against `user_role` directly"
- Also: job notifies MANAGERs (per design only Coordinators should get appointment alerts)

**Spec reference:** `docs/architecture_implementation_plan.md` §A1 — "Never call SELECT role FROM user_role in service logic"

**Files:**
- `backend/src/main/kotlin/com/companyb/companyapp/service/NextAppointmentScheduler.kt` — lines ~103-125

**Fix:**
1. Replace the two-step role+user_role query with a capability-based query:
   ```sql
   SELECT uba.user_id
   FROM user_branch_assignment uba
   JOIN active_user_capabilities auc ON uba.user_id = auc.user_id
   WHERE uba.branch_id = :branchId
     AND uba.ended_at IS NULL
     AND auc.capability_code = 'VIEW_BRANCH_DATA'
     AND auc.context_type = 'BRANCH'
     AND auc.context_id = :branchId
   ```
2. Filter to only Coordinators (not Managers) — per business requirements, only Coordinators get next-appointment alerts
3. Consider adding a dedicated capability code for notification delivery if the coordinator role is the authoritative source

**Priority:** critical
**Story alignment:** US-029 (Next Appointment Alerts)
