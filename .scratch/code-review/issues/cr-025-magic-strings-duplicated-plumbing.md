# CR-025: Magic string constants + duplicated utility patterns (capability gates, path parsing, authz in routes)

**Source:** Chunks 2c (smells: "duplicated capability gate", "duplicated path parsing", "authz in route"), 3 (hard: "magic string ASSIGN_COMPENSATION", smell: "primitive obsession branchTypeName")

**What:**
1. **Magic string `ASSIGN_COMPENSATION`:** Capability code string is hardcoded in service layer instead of referencing a constant from `shared/domain/`.
2. **Duplicated capability gate pattern:** The same `CapabilityService.hasCapability(...)` block is copy-pasted across multiple service methods with only the code/context changing. Should be a single helper.
3. **Duplicated path parsing:** Same `UUID.fromString(context.pathParam(...))` + error handling pattern repeated in multiple routes.
4. **Authorization happening in routes:** Some route handlers check capabilities themselves instead of delegating to the service layer. Per deep module map: "routes parse request, call one service method, return".
5. **Primitive obsession `branchTypeName`:** Branch type is stored/compared as string instead of using the `BranchType` enum.

**Files:**
- `backend/.../service/CompensationService.kt` — magic `ASSIGN_COMPENSATION` string
- `backend/.../api/routes/*.kt` — duplicated path parsing, authz in routes
- `backend/.../service/*.kt` — duplicated capability gates

**Fix:**
1. Replace magic strings with references to capability code constants from `shared/domain/`
2. Extract `callerHasCapability(callerId, code, contextType, contextId)` helper in the service layer
3. Extract `pathParamAsUuid(name: String): UUID` extension in routes
4. Move capability checks from routes to service layer — routes should be thin
5. Use `BranchType` enum everywhere; `branchTypeName` should be derived from the enum, not a string

**Priority:** medium
**Story alignment:** cross-cutting — all service/route files
