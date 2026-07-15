# CR-004: Read endpoints missing capability gates

**Source:** Standards review of US-001–010 (chunk 1)

**What:** Per `backend/AGENTS.md` "Authorization > Read endpoints must also gate on capabilities": "If a write endpoint checks a capability, the corresponding read endpoint (GET) should check the same capability." Several read endpoints are wide open while their write counterparts gate on `MANAGE_USERS`.

**Files:**
- `BranchService.findAll()` — no capability gate (write: `create` checks `MANAGE_USERS`)
- `BranchService.findById()` — no capability gate
- `UserBranchAssignmentService.findActiveByBranch()` — no capability gate (write: `create` checks `MANAGE_USERS`)

**Fix:** Add `capabilityService.hasCapability(callerId, MANAGE_USERS, GLOBAL)` checks to these read methods.

**Priority:** high
**Story alignment:** US-004, US-005
