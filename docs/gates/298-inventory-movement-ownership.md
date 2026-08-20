# Gates - #298: inventory movement request ownership

- [x] G1: Inventory movement UUID retries reject altered immutable request fields
  CHECK: ./gradlew :backend:test --tests 'com.companyb.companyapp.service.BranchInventoryServicePostgresTest.inventory movement UUID retry rejects altered request ownership'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Same-owner inventory movement UUID retries remain idempotent without duplicate stock or audit
  CHECK: ./gradlew :backend:test --tests 'com.companyb.companyapp.service.BranchInventoryServicePostgresTest.inventory movement UUID retry preserves original request'
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
