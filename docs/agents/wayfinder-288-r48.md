Part of #180

## Question

Make inventory movement reason one shared finite wire and persistence type.

## Acceptance

- Add serializable shared `InventoryMovementReason` with existing uppercase values.
- Type inventory request/response DTO reason fields and backend persistence model with it.
- Remove duplicate backend enum and route `valueOf` conversion while preserving endpoint-specific allowed-reason and sign/notes validation.
- Add serialization, malformed-value, endpoint restriction, and database mapping coverage.
