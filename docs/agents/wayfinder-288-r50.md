Part of #180

## Question

Remove implicit Flyway repair from application startup while preserving explicit operator repair.

## Acceptance

- Startup calls Flyway migrate without automatic repair.
- Checksum drift and failed migration state fail closed.
- Clean and repeated startup remain successful.
- Document or test explicit operator repair path without changing migration history.
