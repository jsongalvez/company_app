## Question

Fix terminal relief-access actions so only the target user can receive the existing
terminal response. Currently `ReliefAccessService.grantAccess` returns an already
`GRANTED` request before checking `callerId`, and `denyAccess` returns an already
`DENIED` request before checking `callerId`. An authenticated caller who knows a
request UUID can therefore retrieve another user's relief request details.

Move target authorization ahead of terminal-state idempotency returns. Preserve
same-target idempotency, current state-transition behavior, capability handling,
and response shapes. Add service-level regression tests for unrelated callers on
already-GRANTED and already-DENIED requests.
