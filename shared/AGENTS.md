# Shared Module

Kotlin Multiplatform shared library targeting JVM (backend), Android, and iOS.

## What lives here

| Package | Contents |
|---------|----------|
| `com.companyb.companyapp.contracts.<owner>` | Feature-owned wire contracts (#566: identity, authorization, branch, branchday, client, session, workforce, notification; #567: commerce, commission, finance, remittance, reporting, audit, incident) — related requests/responses/enums grouped in coherent files |
| `com.companyb.companyapp.domain` | `OperationalDay` — the shared operational-day boundary rule (#875: `MANILA_ZONE_ID` + `DAY_BOUNDARY_HOUR` + pure `operationalEpochDay`/`isBeforeCutoff`; backend `BranchDayService` and client `ReliefInviteLogic` delegate); the WireEnums umbrella is eliminated (#567) |
| `com.companyb.companyapp.dto` | `ErrorResponse` protocol only (stays here per #567) |
| `com.companyb.companyapp.validation` | Shared validation logic (`EmailPolicy`, `PasswordPolicy` — both have backend + client consumers, stay) |

## Conventions

- All wire contract types must be `@Serializable` (kotlinx.serialization)
- Wire enums live with their feature owner under `contracts/<owner>` — never redefine them in backend or frontend; backend-only results (e.g. `LoginResult`, `CredentialTokenPurpose`) live in backend `identity/`, never in shared
- Route constants (URL paths) live in `api/ApiRoutes` as the complete centralized catalog — never fragment per feature

## Wire enum evolution policy (#876)

Mobile clients lag server deploys, so additive enum growth must never fail old installs.
Every shared wire enum carries an `UNKNOWN` sentinel as its last entry, and every
response DTO defaults its enum properties to it:

- **Adding a value** (e.g. a new `ExpenseCategory`): append the entry before `UNKNOWN`;
  never rename, remove, or reorder existing entries. No migration: the sentinel is never
  persisted. Add a case to `WireEnumsSerializationTest` decoding your response DTO with
  the new wire string and asserting the sentinel (see the `spoiled` helper).
- **Reads (client)**: the `ApiClient` transport sets `coerceInputValues = true`, so an
  unknown value coerces to the property default (`UNKNOWN`) — one degraded row, not a
  failed list. `UNKNOWN` rows render read-only (label "Unknown", no actions); capability
  contexts/sources that are `UNKNOWN` grant nothing (fail-closed):
  `hasCapabilityAnyContext` excludes `UNKNOWN` rows and the exact-match checks never
  match them, so future contexts never open route gates on old installs.
- **Writes (backend)**: the backend transport stays strict (no coercion), so unknown
  input still fails decode → 400. Request DTOs never default their enum properties.
  Every write path accepting a wire enum must also reject a literal `UNKNOWN` with 400
  (or route it through an exhaustive `when` whose `UNKNOWN` branch throws) so the
  sentinel can never reach the database — the Postgres enum columns have no such label.
- **Exhaustive `when`s**: a new sentinel entry breaks exhaustiveness by design. Backend
  branches fail closed (throw); client branches render the degraded row.

## Key constants

| Constant | Used by |
|----------|---------|
| `OperationalDay.MANILA_ZONE_ID` (`Asia/Manila`) + `DAY_BOUNDARY_HOUR` (4) + `operationalEpochDay`/`isBeforeCutoff` (`domain/OperationalDay`) | Single owner of the operational-day boundary (#875); backend `BranchDayService` and client `ReliefInviteLogic` delegate; thin platform wrappers (`BranchDayService.manilaZone`, `RemittanceUi.ManilaZone`, `TimestampFormat`) build their `ZoneId`/`TimeZone` from the shared id |
| Capability code strings (e.g. `EDIT_BRANCH_DATA`) | Both backend services and frontend UI gating |
