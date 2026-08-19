Part of #180

## Question

Type `Session.sessionType` and `Session.sessionStatus` as shared finite enums throughout the backend persistence model.

## Acceptance

- Replace repository model String fields with shared `SessionType` and `SessionStatus`.
- Remove redundant `.name` and `valueOf` conversions at repository and consumers.
- Preserve database bindings, HTTP wire values, and session behavior.
- Add focused mapping/compile coverage and run shared/backend quality gates.
