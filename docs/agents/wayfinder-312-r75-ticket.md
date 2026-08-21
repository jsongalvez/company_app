Part of #180

## Question

Make Expense UUID retries preserve requested Branch Day and creator ownership under sequential and concurrent attempts.

## Scope

- Move idempotent insert and collision classification into the repository transaction.
- Preserve same-owner retries, Branch Day editability, audit-once behavior, and deterministic conflict errors.
- Reject foreign Branch Day or creator collisions without mutation.
