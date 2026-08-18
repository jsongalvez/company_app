# Gates — 211: registration uniqueness race

- [x] G1: Sequential username and email collisions return existing registration results
  CHECK: ./gradlew :backend:test --tests 'com.companyb.companyapp.service.AuthServicePostgresTest.registration collision*'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Concurrent same-username and same-email registrations produce one success and one conflict without leaked rows
  CHECK: ./gradlew :backend:test --tests 'com.companyb.companyapp.service.AuthServicePostgresTest.concurrent registration*'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Backend static analysis and formatting pass
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
