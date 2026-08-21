# Gates - 241: session idempotency ownership

- [x] G1: Foreign branch retry is rejected
  CHECK: ./gradlew :backend:test --tests '*SessionServicePostgresTest.create session rejects duplicate id from another branch'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Same-owner retry remains idempotent
  CHECK: ./gradlew :backend:test --tests '*SessionServicePostgresTest.create session returns existing on duplicate id'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Session source compiles
  CHECK: ./gradlew :backend:compileKotlin
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
