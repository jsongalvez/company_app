# Shared Module

Kotlin Multiplatform shared library targeting JVM (backend), Android, and iOS.

## What lives here

| Package | Contents |
|---------|----------|
| `com.companyb.companyapp.contracts.<owner>` | Feature-owned wire contracts (#566: identity, authorization, branch, branchday, client, session, workforce, notification; #567: commerce, commission, finance, remittance, reporting, audit, incident) — related requests/responses/enums grouped in coherent files |
| `com.companyb.companyapp.domain` | No main sources — `ErrorCode` removed as dead code in `f126a8bb` (ref #530; dead-code ownership now lives in `config/deadcode/` + `detekt-rules`); the WireEnums main-source umbrella was grouped into feature-owned contracts in `319f8c97` (ref #567) — only the historical `commonTest/.../domain/WireEnumsSerializationTest.kt` name survives, covering feature-owned `contracts/*` enums |
| `com.companyb.companyapp.dto` | `ErrorResponse` protocol only (stays here per #567) |
| `com.companyb.companyapp.validation` | Shared validation logic (`EmailPolicy`, `PasswordPolicy` — both have backend + client consumers, stay) |

## Conventions

- All wire contract types must be `@Serializable` (kotlinx.serialization)
- Wire enums live with their feature owner under `contracts/<owner>` — never redefine them in backend or frontend; backend-only results (e.g. `LoginResult`, `CredentialTokenPurpose`) live in backend `identity/`, never in shared
- Route constants (URL paths) live in `api/ApiRoutes` as the complete centralized catalog — never fragment per feature

## Key constants

| Constant | Used by |
|----------|---------|
| `ManilaZone` (`Asia/Manila` timezone) | No shared home — backend defines its own `manilaZone` in `branchday/BranchDayService`; client defines `internal ManilaZone` in `remittance/RemittanceUi` plus inline `TimeZone.of` sites; shared boundary-rule ownership belongs to #875 |
| Capability code strings (e.g. `EDIT_BRANCH_DATA`) | Both backend services and frontend UI gating |
