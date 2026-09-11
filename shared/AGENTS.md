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

## Key constants

| Constant | Used by |
|----------|---------|
| `OperationalDay.MANILA_ZONE_ID` (`Asia/Manila`) + `DAY_BOUNDARY_HOUR` (4) + `operationalEpochDay`/`isBeforeCutoff` (`domain/OperationalDay`) | Single owner of the operational-day boundary (#875); backend `BranchDayService` and client `ReliefInviteLogic` delegate; thin platform wrappers (`BranchDayService.manilaZone`, `RemittanceUi.ManilaZone`, `TimestampFormat`) build their `ZoneId`/`TimeZone` from the shared id |
| Capability code strings (e.g. `EDIT_BRANCH_DATA`) | Both backend services and frontend UI gating |
