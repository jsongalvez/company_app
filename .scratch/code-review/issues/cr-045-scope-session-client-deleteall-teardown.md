# CR-045: Scope SessionTable + ClientTable deleteAll() in test teardown

**What to build:** Replace unscoped `SessionTable.deleteAll()` and `ClientTable.deleteAll()` in test teardown methods with scoped `deleteWhere` calls, so teardown only removes rows created by that test class. This prevents test isolation bugs where one test class wipes data another depends on.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Track session/client IDs as class-level fields where missing (promote inline `UUID.randomUUID()` to `private val`)
- [ ] Replace `SessionTable.deleteAll()` with `deleteWhere { SessionTable.branchDayId eq branchDayId }` (or `clientId`) in all affected test files
- [ ] Replace `ClientTable.deleteAll()` with `deleteWhere { ClientTable.id eq clientId }` in all affected test files
- [ ] Run `./gradlew :backend:test` to confirm no regressions

**Files affected:**
- `DailySalesSummaryServicePostgresTest.kt` (SessionTable, ClientTable)
- `CommissionServicePostgresTest.kt` (SessionTable)
- `ConcernServicePostgresTest.kt` (SessionTable, ClientTable)
- `MonthlyRemittanceSummaryServicePostgresTest.kt` (SessionTable)
- `NotificationServicePostgresTest.kt` (SessionTable, ClientTable)
- `ProductSaleServicePostgresTest.kt` (SessionTable, ClientTable)
- `RemittanceServicePostgresTest.kt` (SessionTable)
- `SessionServicePostgresTest.kt` (SessionTable, ClientTable)
- `RemittanceLineServicePostgresTest.kt` (SessionTable, ClientTable)
- `ExportServicePostgresTest.kt` (SessionTable, ClientTable)
