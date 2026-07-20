# CR-024: Audit written in repo layer + no transaction{} in snapshot repo

**Source:** Chunk 2c (standards hard: "audit written directly in repo", "no transaction{} in snapshot repo")

**What:**
1. **Audit written in repo layer:** Per `docs/architecture_implementation_plan.md` §G1: "Audit entries are written in the service layer only. Never in repositories." At least one repository class calls `AuditLogRepository.record()` directly instead of having the service layer handle it.
2. **No transaction{} in snapshot repo:** The `RemittanceFinancialSnapshot` repository performs DB operations outside an explicit `transaction {}` block — relies on implicit transaction management which may cause the snapshot write to not be atomic with the remittance submission.

**Files:**
- `backend/.../repository/` — search for `AuditLogRepository` calls in `*.kt` files under `repository/`
- `backend/.../repository/RemittanceSnapshotRepository.kt` (or similar)

**Fix:**
1. Move all `AuditLogRepository.record()` calls from repository layer to service layer methods
2. Wrap snapshot repository operations in explicit `transaction {}` blocks
3. Verify audit + data mutation happen in the same `transaction {}` (Exposed reuses the connection)

**Priority:** medium
**Story alignment:** US-020 (Remittance), G1 (Audit Logging)
