# Shared Module

Kotlin Multiplatform shared library targeting JVM (backend), Android, and iOS.

## What lives here

| Package | Contents |
|---------|----------|
| `com.companyb.companyapp.contracts.<owner>` | Feature-owned wire contracts (#566: identity, authorization, branch, branchday, client, session, workforce, notification; #567: commerce, commission, finance, remittance, reporting, audit, incident) — related requests/responses/enums grouped in coherent files |
| `com.companyb.companyapp.domain` | Remaining cross-feature wire enums until #567 eliminates the umbrella (`Remittance*`, `ExpenseCategory`, `InventoryMovementReason`, `AuditAction`, `IncidentSource`); `ErrorCode` has no consumers (dead-code discovery owns it) |
| `com.companyb.companyapp.dto` | Remaining #567 families (finance/commerce/remittance/audit/reporting/incident) until grouped into contracts; `ErrorResponse` protocol stays here per #567 |
| `com.companyb.companyapp.validation` | Shared validation logic (`EmailPolicy`, `PasswordPolicy` — both have backend + client consumers, stay) |

## Conventions

- All wire contract types must be `@Serializable` (kotlinx.serialization)
- Wire enums live with their feature owner under `contracts/<owner>` — never redefine them in backend or frontend; backend-only results (e.g. `LoginResult`, `CredentialTokenPurpose`) live in backend `identity/`, never in shared
- Route constants (URL paths) live in `api/ApiRoutes` as the complete centralized catalog — never fragment per feature

## Key constants

| Constant | Used by |
|----------|---------|
| `ManilaZone` (`Asia/Manila` timezone) | Backend for day-state/boundary calculations |
| Capability code strings (e.g. `EDIT_BRANCH_DATA`) | Both backend services and frontend UI gating |
