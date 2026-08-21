# Gates — #291: keep soft-deleted expenses immutable under races

- [x] G1: Expense update atomically excludes soft-deleted rows
  CHECK: grep -n -A6 'ExpenseTable.version eq expectedVersion' backend/src/main/kotlin/com/companyb/companyapp/repository/ExpenseRepository.kt | grep 'ExpenseTable.deletedAt.isNull()'
  EXPECT: EXIT 0
  EVIDENCE: 151-                        ExpenseTable.deletedAt.isNull()

- [x] G2: Regression test covers stale update after soft delete
  CHECK: ./gradlew :backend:test --tests 'com.companyb.companyapp.service.ExpenseServicePostgresTest.update after soft delete race is rejected'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
