# Gates - #295 session-practitioner version races

- [x] G1: Session-practitioner version conflicts use typed domain exception
  CHECK: grep -R 'check(updated > 0) { "Session version changed concurrently"' -n backend/src/main backend/src/test
  EXPECT: EXIT 1
  EVIDENCE: exit 1

- [x] G2: Session-practitioner persistence tests pass
  CHECK: ./gradlew :backend:test --tests '*SessionServicePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G3: Backend formatting and static analysis pass
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
