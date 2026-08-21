# Gates - 234: remittance Undo database time

- [x] G1: Production Undo path does not supply JVM time to expiry comparison
  CHECK: grep -q 'now = OffsetDateTime.now' backend/src/main/kotlin/com/companyb/companyapp/service/finance/remittance/RemittanceService.kt
  EXPECT: EXIT 1
  EVIDENCE: exit 1

- [x] G2: Existing remittance Undo boundary and fallback tests pass
  CHECK: ./gradlew :backend:test --tests '*RemittanceUndoServicePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Backend static analysis and shared JVM compilation pass
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck :shared:compileKotlinJvm
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
