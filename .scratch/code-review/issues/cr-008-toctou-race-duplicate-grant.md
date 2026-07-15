# CR-008: TOCTOU race in duplicate relief grant handling

**Source:** Spec review of US-001–010 (chunk 1)

**What:** US-009 spec says "Handle `idx_one_grant_per_day` unique index: silently return HTTP 200 on duplicate grants for `requestedBy + branchDayId`". The implementation in `ReliefAccessService.grantAccess` uses a pre-check (`findByRequestedByAndBranchDayId`) before inserting. Under concurrent requests for the same `requestedBy + branchDayId` but different `requestId`, a TOCTOU race lets both pass the pre-check, then the second `UserCapabilityTable.insert` throws, producing HTTP 500 instead of the required HTTP 200.

**Spec reference:** US-009: "Handle idx_one_grant_per_day unique index: silently return HTTP 200 on duplicate grants"

**Fix:** Replace the pre-check with an `insertIgnore` (which returns 0 rows affected on conflict) + follow-up read. Or catch the constraint violation exception and return the existing row.

**Priority:** high
**Story alignment:** US-009
