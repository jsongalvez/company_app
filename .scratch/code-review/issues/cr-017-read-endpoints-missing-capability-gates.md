# CR-017: Missing capability gates on read endpoints (all chunks)

**Source:** Chunks 2a (standards hard: 4 read endpoints), 2b (standards hard: 4 missing gates), 3 (standards hard: no gates on audit reads)

**What:**
Per `backend/AGENTS.md` "Read endpoints must also gate on capabilities": "If a write endpoint checks a capability, the corresponding read endpoint should check the same capability." Multiple read endpoints are missing capability gates while their write counterparts check capabilities:

**Chunk 2a:**
- `BranchService.findAll()` / `findById()` — no gate (write: `create` checks `MANAGE_USERS`)
- `UserBranchAssignmentService.findActiveByBranch()` — no gate
- Several session/attendance reads missing gates

**Chunk 2b:**
- 4 additional read endpoints missing capability gates (investigate and list)

**Chunk 3:**
- Audit read endpoints have NO capability gates — anyone can read the complete audit log

**Fix:**
1. Audit all GET/read endpoints and verify they have the same capability check as their corresponding write (POST/PATCH/DELETE) endpoint
2. Add `CapabilityService.hasCapability()` checks to each missing read method
3. For audit reads: gate on `VIEW_BRANCH_DATA` (BRANCH scope) or add a dedicated `VIEW_AUDIT_LOG` capability
4. Write a detekt rule or integration test that asserts: every GET matches capability of its POST/PATCH sibling

**Priority:** high
**Story alignment:** cross-cutting — all read endpoints
