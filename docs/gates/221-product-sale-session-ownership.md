# Gates - 221: product-sale session ownership

- [x] G1: Cross-branch session/branch-day sale is rejected before writes
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.ProductSaleServicePostgresTest.sell\ rejects\ session\ from\ another\ branch\ day
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Product-sale service regression suite passes
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.ProductSaleServicePostgresTest
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Backend static quality gate passes
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G4: No whitespace errors
  CHECK: git diff --check
  EXPECT: EXIT 0
  EVIDENCE: exit 0
