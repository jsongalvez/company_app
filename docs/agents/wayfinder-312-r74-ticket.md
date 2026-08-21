Part of #180

## Question

Persist JWT revocation state so deactivation followed by reactivation and server restart cannot revive an old JWT.

## Scope

- Persist revocation epoch independently from reversible `deactivated_at`.
- Reject tokens issued before the persisted revocation boundary after restart and reactivation.
- Preserve fresh-token acceptance, repeated deactivation, deny-list startup behavior, and token boundary semantics.
