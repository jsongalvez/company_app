# CR-028: Business logic in route handler + fabricated domain objects on delete

**Source:** Chunk 4 (standards hard: "business logic in route handler", "fabricated domain objects on delete", "inconsistent state pattern")

**What:**
1. **Business logic in route handler:** At least one route handler performs business logic (validation, domain computation) that should be in the service layer. Violates deep module map: "routes parse request, call one service method, return."
2. **Fabricated domain objects on delete:** Soft-delete endpoints construct fake response objects with blank/default values instead of returning the actual soft-deleted record. This loses data in the API response.
3. **Inconsistent state pattern:** `_uiState` handling differs across ViewModels — some use `MutableStateFlow`, others use `MutableState`, mutation patterns vary.

**Files:**
- `backend/.../api/routes/*.kt` — search for business logic in route handlers
- `backend/.../service/` — soft-delete methods (ExpenseService, SessionService, etc.)
- `composeApp/src/commonMain/kotlin/.../viewmodel/*.kt` — state handling

**Fix:**
1. Move any business logic from route handlers into service layer methods
2. Return the actual soft-deleted record (with `deletedAt` set), not a fabricated empty object
3. Standardize ViewModel state pattern: use `MutableStateFlow<UiState>` + sealed `UiState` class across all ViewModels
4. Extract state update helper (builds on CR-015)

**Priority:** medium
**Story alignment:** cross-cutting — all composeApp ViewModels + backend routes
