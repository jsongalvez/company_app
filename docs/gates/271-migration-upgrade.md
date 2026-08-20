# Gates — #271: JWT revocation migration upgrade

- [x] G1: Migration upgrade test passes from V21 through V22
  CHECK: ./gradlew :backend:test --tests '*MigrationUpgradePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Migration test compiles and passes test-source formatting
  CHECK: ./gradlew :backend:ktlintTestSourceSetCheck :backend:test --tests '*MigrationUpgradePostgresTest'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
