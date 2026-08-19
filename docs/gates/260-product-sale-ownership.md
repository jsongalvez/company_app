# Gates - 260: product-sale UUID ownership

- [x] G1: Product-sale ownership regression tests pass
  CHECK: ./gradlew :backend:test --tests '*ProductSaleServicePostgresTest.sell rejects existing sale id from another creator' --tests '*ProductSaleServicePostgresTest.sell rejects existing sale id with altered request' -x :backend:publishOpenApiSpec
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.

- [x] G2: Product-sale static quality checks pass
  CHECK: ./gradlew :backend:detekt :backend:ktlintCheck -x :backend:publishOpenApiSpec
  EXPECT: EXIT 0
  EVIDENCE: Reusing configuration cache.
