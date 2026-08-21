# Gates — #253: remittance draft Branch ownership

- [x] G1: UUID retry from another Branch is rejected as a conflict
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceDraftOwnershipPostgresTest.create draft UUID collision across branches returns conflict'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: concurrent cross-Branch UUID collision returns one conflict and one audit
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceDraftOwnershipPostgresTest.concurrent draft UUID collision across branches returns one conflict' >/dev/null 2>&1
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G3: same-Branch UUID retry remains idempotent
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceServicePostgresTest.create draft idempotent duplicate returns existing'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G4: remittance service regression suite passes
  CHECK: ./gradlew :backend:test -x :backend:publishOpenApiSpec --tests 'com.companyb.companyapp.service.RemittanceServicePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
