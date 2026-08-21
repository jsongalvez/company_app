## Question

Make remittance-line UUID retries request-owned. A retry with the same `(lineId, remittanceId)` must return the existing line only when immutable request fields match: type, session/product source, amount, and creator. An altered retry must fail with a deterministic conflict without version or audit mutation. Preserve same-request idempotency and cross-parent conflict behavior.

## Validation

- Add focused service/repository regressions for same request, altered type, altered source, altered amount, altered creator, and concurrent collision.
- Run remittance-line tests, backend quality gates, OpenAPI/cleanliness checks, and the standard review profile.
