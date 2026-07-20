# CR-032: MeService — duplicate capability query + returns INACTIVE users

**Source:** Chunk 5 (smell: "MeService duplicates capability query", spec: "MeService.getMe returns data for INACTIVE users")

**What:**
1. **Duplicate capability query:** `MeService.getMe()` queries capabilities separately from the existing `CapabilityService` — should reuse `CapabilityService` or the `active_user_capabilities` view directly via Exposed DSL.
2. **Returns INACTIVE users:** `MeService.getMe()` does not filter by `app_user.status = 'ACTIVE'`. An INACTIVE user who still has a valid JWT (within 24h, not yet in deny list) can query `/api/me` and get their user data.

**Spec reference:** `docs/architecture_implementation_plan.md` §A5: "setting a user INACTIVE immediately revokes all capability checks"

**Files:**
- `backend/.../service/MeService.kt` — `getMe()` method

**Fix:**
1. Refactor `getMe()` to reuse `CapabilityService` for capability listing, or ensure the query goes through `active_user_capabilities` view
2. Add `AND status = 'ACTIVE'` filter to the user query in `getMe()`
3. Alternatively: ensure the JWT deny list catches INACTIVE users before they reach this endpoint

**Priority:** high
**Story alignment:** US-003 (User Profile), A5 (Inactive Revocation)
