-- #357: relief-request broadcast model (the #352 rules changes).
--
-- 1. Targeting retired: a request no longer names a specific checked-in user; the whole
--    branch hears it. The target_user column dies with the selection rule.
--
-- 2. Flood control (#352 Q4): one live request per requester per branch day. Legacy data
--    may hold several PENDING rows per (requester, day) under the old targeting model —
--    keep one survivor, delete the rest (meaningless once targeting is gone; the audit
--    log retains their history). Then the partial unique index enforces the rule.
--
-- 3. CANCELLED joins relief_status as the withdraw/cancel terminal state (distinct from
--    DENIED so outcome messaging can tell them apart).

ALTER TABLE grant_relief_access DROP COLUMN target_user;

DELETE FROM grant_relief_access
WHERE request_status = 'PENDING'
  AND id NOT IN (
      SELECT DISTINCT ON (requested_by, branch_day_id) id
      FROM grant_relief_access
      WHERE request_status = 'PENDING'
      ORDER BY requested_by, branch_day_id, id
  );

CREATE UNIQUE INDEX idx_one_live_relief_request
    ON grant_relief_access (requested_by, branch_day_id) WHERE request_status = 'PENDING';

ALTER TYPE relief_status ADD VALUE IF NOT EXISTS 'CANCELLED' AFTER 'DENIED';
