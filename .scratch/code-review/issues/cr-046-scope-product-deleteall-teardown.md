# CR-046: Scope product-related deleteAll() in test teardown

**What to build:** Replace unscoped `ProductTable.deleteAll()`, `ProductCategoryTable.deleteAll()`, and `ProductSaleTable.deleteAll()` in test teardown methods with scoped `deleteWhere` calls. These tables are shared across 5+ test classes, so unscoped deletes break test isolation.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Track product/category IDs as class-level fields where missing
- [ ] Replace `ProductTable.deleteAll()` with `deleteWhere { ProductTable.id eq productId }` or scoped by category
- [ ] Replace `ProductCategoryTable.deleteAll()` with `deleteWhere { ProductCategoryTable.id eq categoryId }`
- [ ] Replace `ProductSaleTable.deleteAll()` with `deleteWhere { ProductSaleTable.branchDayId eq branchDayId }`
- [ ] Run `./gradlew :backend:test` to confirm no regressions

**Files affected:**
- `DailySalesSummaryServicePostgresTest.kt` (ProductTable, ProductCategoryTable, ProductSaleTable)
- `MonthlyRemittanceSummaryServicePostgresTest.kt` (ProductTable, ProductCategoryTable, ProductSaleTable)
- `RemittanceServicePostgresTest.kt` (ProductTable, ProductCategoryTable, ProductSaleTable)
- `RemittanceLineServicePostgresTest.kt` (ProductTable, ProductCategoryTable, ProductSaleTable)
- `ExportServicePostgresTest.kt` (ProductTable, ProductCategoryTable, ProductSaleTable)
