# Gates — #254: allowance Branch Day ownership

- [x] G1: UUID retry from another Branch Day is rejected without a second audit
  CHECK: `./gradlew :backend:test --tests com.companyb.companyapp.service.AllowanceServicePostgresTest.create\ rejects\ UUID\ collision\ from\ another\ branch\ day\ without\ auditing -x :backend:publishOpenApiSpec`
  EXPECT: EXIT 0
  EVIDENCE: Focused AllowanceServicePostgresTest passed.

- [x] G2: concurrent cross-Branch Day UUID collision returns one success and one not found
  CHECK: `./gradlew :backend:test --tests com.companyb.companyapp.service.AllowanceServicePostgresTest.concurrent\ UUID\ collision\ from\ another\ branch\ day\ returns\ one\ success\ and\ one\ not\ found -x :backend:publishOpenApiSpec`
  EXPECT: EXIT 0
  EVIDENCE: Focused AllowanceServicePostgresTest passed.

- [x] G3: same-Branch Day UUID retry remains idempotent
  CHECK: `./gradlew :backend:test --tests com.companyb.companyapp.service.AllowanceServicePostgresTest.create\ idempotent\ duplicate\ returns\ existing -x :backend:publishOpenApiSpec`
  EXPECT: EXIT 0
  EVIDENCE: Focused AllowanceServicePostgresTest passed.

- [x] G4: backend quality, tests, shared JVM compile, and test cleanliness pass
  CHECK: `./gradlew :backend:detekt :backend:ktlintCheck :backend:test :shared:compileKotlinJvm -x :backend:publishOpenApiSpec`; `bash scripts/check-test-cleanliness.sh`
  EXPECT: EXIT 0
  EVIDENCE: Full Gradle gate passed in 9m06s; cleanliness reported all test tables clean.

Note: `:backend:publishOpenApiSpec` remains excluded because current route-contract fingerprint is stale before this change, as documented in prior Map #180 handoffs.
