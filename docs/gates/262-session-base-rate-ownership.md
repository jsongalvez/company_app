# Gates - #262: session-base-rate ownership

- [x] G1: Existing UUID retries validate branch and immutable request ownership
  CHECK: ./gradlew :backend:test --tests com.companyb.companyapp.service.SessionBaseRateServicePostgresTest
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Backend production compilation passes
  CHECK: ./gradlew :backend:compileKotlin
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Shared JVM compilation passes
  CHECK: ./gradlew :shared:compileKotlinJvm
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
