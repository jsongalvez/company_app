# CR-047: Scope remaining shared-table deleteAll() in test teardown

**What to build:** Replace unscoped `BranchDayTable.deleteAll()`, `BranchTable.deleteAll()`, `ExpenseTable.deleteAll()`, `CompensationTable.deleteAll()`, and `AuditLogTable.deleteAll()` in test teardown methods with scoped `deleteWhere` calls. These tables are referenced by foreign keys from child tables, so unscoped deletes cascade or break other tests.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Replace `BranchDayTable.deleteAll()` with `deleteWhere { BranchDayTable.branchId eq branchId }`
- [ ] Replace `BranchTable.deleteAll()` with `deleteWhere { BranchTable.id eq branchId }`
- [ ] Replace `ExpenseTable.deleteAll()` with `deleteWhere { ExpenseTable.branchDayId eq branchDayId }` (or `createdBy eq callerId`)
- [ ] Replace `CompensationTable.deleteAll()` with `deleteWhere` scoped by branchDayId or assignedBy
- [ ] Replace `AuditLogTable.deleteAll()` with `deleteWhere { AuditLogTable.changedBy eq callerId }`
- [ ] Run `./gradlew :backend:test` to confirm no regressions

**Files affected:**
- `ConcernServicePostgresTest.kt` (BranchDayTable, BranchTable)
- `MonthlyRemittanceSummaryServicePostgresTest.kt` (AuditLogTable, CompensationTable, ExpenseTable)
- `SessionServicePostgresTest.kt` (BranchDayTable, BranchTable)
- `CompensationServicePostgresTest.kt` (CompensationTable)
- `RemittanceLineServicePostgresTest.kt` (BranchDayTable)
- `ExportServicePostgresTest.kt` (CompensationTable, ExpenseTable)
