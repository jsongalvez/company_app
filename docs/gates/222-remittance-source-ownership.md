# Gates - 222: remittance source ownership

- [x] G1: Cross-branch session and product-sale sources are rejected before line writes
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.RemittanceLineServicePostgresTest
  EXPECT: EXIT 0
  EVIDENCE: 30 tests passed.

- [x] G2: Backend static quality and integration gate passes
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck :backend:test
  EXPECT: EXIT 0
  EVIDENCE: EXIT 0; 8m15s.

- [x] G3: Test database is clean
  CHECK: bash scripts/check-test-cleanliness.sh
  EXPECT: EXIT 0 and all test tables clean
  EVIDENCE: All test tables are clean.

- [x] G4: No whitespace errors
  CHECK: git diff --check
  EXPECT: EXIT 0
  EVIDENCE: EXIT 0.
