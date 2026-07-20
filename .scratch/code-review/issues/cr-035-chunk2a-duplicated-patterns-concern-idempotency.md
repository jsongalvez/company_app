# CR-035: Chunk 2a — duplicated checkEditBranchData + version-increment pattern + extra GET per-session concerns + server-generated UUID

**Source:** Chunk 2a (smells: "duplicated checkEditBranchData", "version-increment pattern"; creep: "extra GET per-session concerns", "server-generated UUID in ConcernService")

**What:**
1. **Duplicated `checkEditBranchData`:** The same `EDIT_BRANCH_DATA` capability check + `assertEditable` call is copy-pasted across multiple service methods. Should be a single helper method.
2. **Version-increment pattern:** `SELECT version, then UPDATE SET version = version + 1` is duplicated across services. Should be extracted into a reusable repository helper.
3. **Extra GET per-session concerns:** Session creation response includes a separate GET call to fetch concerns, doubling DB round-trips.
4. **Server-generated UUID in ConcernService:** The UUID for a new concern is generated server-side instead of being client-provided for idempotency — violates F1 idempotency rule.

**Files:**
- `backend/.../service/SessionService.kt` — `checkEditBranchData`, version-increment
- `backend/.../service/ConcernService.kt` — server-generated UUID

**Fix:**
1. Extract `checkBranchDayEditable(callerId, branchDayId)` helper covering both capability check + assertEditable
2. Extract `incrementVersion(table, id, expectedVersion)` extension
3. Include concerns in session creation response (join/lazy load) — remove extra round-trip
4. Accept client-generated UUID for new concerns, use idempotent insert

**Priority:** low
**Story alignment:** US-012 (Session Create), US-017 (Concerns)
