# Shared Module

Kotlin Multiplatform shared library targeting JVM (backend), Android, and iOS.

## What lives here

| Package | Contents |
|---------|----------|
| `com.companyb.companyapp.domain` | Sealed classes, enums (`SessionType`, `BranchType`, `SessionStatus`, `Gender`), capability codes |
| `com.companyb.companyapp.dto` | Serializable request/response objects shared between frontend and backend |
| `com.companyb.companyapp.validation` | Shared validation logic |

## Conventions

- All types must be `@Serializable` (kotlinx.serialization)
- Domain enums are shared across all modules — never redefine them in backend or frontend
- Route constants (URL paths) live here so both backend and frontend reference the same strings

## Key constants

| Constant | Used by |
|----------|---------|
| `ManilaZone` (`Asia/Manila` timezone) | Backend for day-state/boundary calculations |
| Capability code strings (e.g. `EDIT_BRANCH_DATA`) | Both backend services and frontend UI gating |
