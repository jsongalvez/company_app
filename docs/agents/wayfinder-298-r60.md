## Question

Make attendance clock-in idempotency preserve caller and Branch ownership. A retry with an existing
attendance UUID must not return another user's attendance or ignore a mismatched Branch. Keep
clock-in open to authenticated users and preserve same-owner retries, one-active-clock-in rules,
assignment/audit behavior, and commission side effects. Add foreign caller, wrong Branch, repeated
attempt, and audit/assignment regression coverage. See Map #180 Session 298 R60 dossier.
