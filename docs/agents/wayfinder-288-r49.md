Part of #180

## Question

Make product-sale and attendance commission-trigger mutations atomic with commission recalculation and split replacement.

## Acceptance

- Source mutation, commission calculation, split replacement, and audit effects commit or roll back together.
- Cover product sale, clock-in, clock-out, and relevant manual inclusion trigger paths without generic transaction abstractions.
- Preserve PAST/REMITTED behavior, idempotency, authorization, and commission eligibility rules.
- Add failure-injection, concurrent/repeated recalculation, and no-transient-empty-split tests.
