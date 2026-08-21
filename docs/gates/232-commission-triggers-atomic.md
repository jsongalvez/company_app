# Gates — #232: make commission triggers atomic

- [x] G1: Product-sale commission trigger rolls back source mutation, inventory movement, audit, and splits together
  CHECK: ./gradlew :backend:test --tests '*CommissionServicePostgresTest' --tests '*ProductSaleServicePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Attendance and manual-inclusion commission triggers preserve atomicity and idempotency
  CHECK: ./gradlew :backend:test --tests '*CommissionServicePostgresTest' --tests '*AttendanceServicePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Calculating task graph as no cached configuration is available for tasks: :backend:test --tests *CommissionServicePostgresTest --tests *AttendanceServicePostgresTest

- [x] G3: Backend static analysis and formatting pass
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
