# Build: preserve session-base-rate ownership on UUID retries

## Question

Session-base-rate creation must check UUID ownership before closing the active rate. Same-Branch retries return unchanged data; foreign Branch or altered retries fail without mutating rate windows or audit state. Concurrent distinct creations retain one active rate.

## Scope

- `SessionBaseRateRepository` and service route tests.
- Preserve `MANAGE_PRODUCTS` authorization, no-overlap constraint, audit behavior, and rate semantics.

## Acceptance

- Existing UUID lookup and Branch ownership happen before any rate update.
- Same-Branch same-request retry leaves active rate unchanged and does not duplicate audit.
- Foreign Branch or mismatched request cannot disclose or mutate rate data.
- Concurrent distinct UUID writes classify conflicts deterministically and preserve one active rate.
