# Child: backend Detekt enforcement

Part of parent #274; blocked by shared enforcement child.

## Question

How should validated safety policy cover backend production and test code while preserving runtime behavior and database gate integrity?

## Acceptance

- Apply only validated rules and exact source-set tasks.
- Resolve findings without broad exclusions or behavior changes unless a finding proves a correctness defect.
- Preserve audit, auth, database, and test-data-cleanliness behavior.
- Run negative control, focused Detekt/tests, full backend quality gate, shared JVM compile, and required review lenses.
