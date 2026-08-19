# Build: authorize attendance clock-out ownership

Part of Map #180.

## Question

Ensure `POST /api/attendance/clock-out` cannot mutate another user's attendance. Preserve self clock-out idempotency, audit attribution, and commission recalculation behavior. Add authenticated-user regression coverage for foreign attendance rejection and authorized repeated self clock-out.
