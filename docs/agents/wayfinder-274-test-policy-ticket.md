# Child: Compose and shared test Detekt enforcement

Part of parent #274; blocked by Compose production enforcement child.

## Question

How should shared and Compose test source sets receive safety enforcement without weakening production safety or hiding test defects?

## Acceptance

- Enumerate every applicable common/platform test task and its actual source coverage.
- Keep safety rules active in tests; isolate only reviewed test-style exceptions in separate config.
- If a source set has no task, record the exact limitation and smallest follow-up.
- Run negative control, focused test/Detekt checks, cleanup validation, and P1-P4 review.
