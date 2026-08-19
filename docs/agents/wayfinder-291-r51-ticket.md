## Question

Make remittance Undo expiry use one authoritative transaction clock. The current Undo path compares a JVM timestamp with a PostgreSQL-stamped submission timestamp, so clock skew can cross the 48-hour business boundary incorrectly. Preserve existing Undo behavior, snapshot fallback, SERIALIZABLE locking, audit atomicity, and testability without introducing a universal clock abstraction.

## Evidence

- `RemittanceService.undo` supplies `OffsetDateTime.now(ZoneOffset.UTC)`.
- `RemittanceRepository.undo` compares that value with `submitted_at` or `snapshotted_at`.
- Submission timestamps use PostgreSQL `CurrentTimestampWithTimeZone`.

## Acceptance

- Undo expiry comparison uses PostgreSQL transaction time.
- Existing inclusive 48-hour boundary and snapshot fallback remain unchanged.
- Existing Undo, audit, serialization, detekt, ktlint, backend test, shared compile, and cleanliness gates pass.
- Skewed-clock boundary coverage proves JVM/DB divergence cannot alter authorization.
