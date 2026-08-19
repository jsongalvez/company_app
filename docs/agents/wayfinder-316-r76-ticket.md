# Build: preserve product-sale creator ownership on UUID retries

## Question

Product-sale UUID retries currently validate only Branch Day ownership. Preserve idempotency for the same creator and Branch Day, but reject foreign creator or altered request ownership without inventory, commission, or audit side effects. Add sequential and concurrent regression coverage.

## Scope

- `ProductSaleRepository` and `ProductSaleService` retry classification.
- Product-sale service Postgres tests.
- Preserve existing Branch Day editability/capability gates, inventory locking, commission recalculation, and audit callback semantics.

## Acceptance

- Same creator, same Branch Day, same request retries return original sale without mutation or duplicate audit.
- Foreign creator or mismatched immutable request context cannot receive or reuse sale UUID.
- Foreign Branch Day remains rejected.
- Concurrent same-UUID attempts classify ownership deterministically and do not duplicate inventory movement or audit rows.
