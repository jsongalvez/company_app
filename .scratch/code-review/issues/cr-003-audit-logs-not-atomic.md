# CR-003: Audit logs not atomic with mutations

**Source:** Standards review of US-001–010 (chunk 1)

**What:** Per `backend/AGENTS.md` "Audit logging": "call `AuditLogRepository.record` inside the same repository `transaction {}` that performs the mutation." Most services call `AuditLogRepository.record(...)` AFTER the repository mutation method returns — meaning the audit insert happens in a *separate* transaction from the mutation it describes. If the audit write fails, the mutation is already committed; if the mutation fails, the audit is never written.

**Files (violating):**
- `AttendanceService.clockIn`
- `AttendanceService.clockOut`
- `MedicalMissionDelegateService.assignDelegate`
- `MedicalMissionDelegateService.revokeDelegate`
- `ReliefAccessService.requestAccess`
- `ReliefAccessService.grantAccess`
- `ReliefAccessService.denyAccess`
- `UserBranchAssignmentService.create`
- `UserBranchAssignmentService.delete`
- `UserBranchAssignmentService.updateSlot`
- `UserBranchAssignmentService.swapSlots`

**Correct example:** `BranchRepository.create` writes audit inside its own `transaction {}`.

**Fix:** For each service, move the `AuditLogRepository.record` call inside the repository's `transaction {}` block — after the mutation but before the closing brace. For repositories that don't currently own the transaction, restructure so they do.

**Priority:** critical
**Story alignment:** US-003, US-005, US-006, US-007, US-008, US-009, US-010

**Status:** ✅ done
