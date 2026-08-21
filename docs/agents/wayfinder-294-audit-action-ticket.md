Part of #180

## Question

How should the finite Audit Log action contract be owned so backend and Compose cannot drift or accept invalid action values?

## Resolution context

The PostgreSQL `audit_action` enum and backend `AuditAction` model already restrict values to `INSERT`, `UPDATE`, and `DELETE`. The shared `AuditLogEntryResponse.action` remains `String`, while Compose redeclares the same three values and parses them locally. Existing finite shared wire enums preserve uppercase serialized values and reject unknown values. Implement the smallest shared enum migration: add a serializable shared `AuditAction`, type the response field, map backend output directly, and remove the Compose duplicate. Preserve query parameter validation at the backend route boundary and add serialization/unknown-value regression coverage.

## Acceptance

- Shared `AuditAction` is the sole Kotlin wire-contract owner for audit action values.
- Serialized values remain uppercase and byte-compatible.
- Unknown response values fail closed during deserialization.
- Backend response mapping no longer converts the action to an unrestricted string.
- Compose no longer redeclares or parses audit action vocabulary locally.
- Existing endpoint filtering and UI behavior remain covered by focused tests.
