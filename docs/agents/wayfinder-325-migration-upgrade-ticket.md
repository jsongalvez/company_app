## Question

Build: add disposable migration-upgrade coverage for JWT revocation backfill.

Exercise migration from V21 through V22 with inactive users having varied deactivation
timestamps, then verify `jwt_revoked_at` backfill and old-token rejection after startup.
Keep production schema and migration history unchanged.

Acceptance:

- Upgrade fixture starts at V21 and applies V22 in a disposable database.
- Backfill behavior is asserted for representative inactive users.
- Restart revocation behavior is asserted through the existing auth path.
- Test database cleanup remains fail-closed.
