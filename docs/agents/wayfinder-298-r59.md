## Question

Make session-create idempotency preserve request ownership. A retry with an existing client UUID
must not return a session from another branch or caller context. Preserve legitimate same-request
retries, capability gates, and no mutation or audit on rejected collisions. Add regression coverage
for same-owner retry, foreign branch, foreign caller, wrong day, duplicate UUID race, and audit
invariants. See Map #180 Session 298 R59 dossier.
