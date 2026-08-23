-- #374 — accepted-invite revocation (#363 owner rulings): any active branch member may
-- revoke an ACCEPTED future relief duty, removing the day grant atomically. REVOKED is
-- terminal like DECLINED/RETRACTED and frees the per-person slot (the partial unique
-- index idx_one_pending_accepted_invite covers PENDING/ACCEPTED only).
ALTER TYPE relief_invite_status ADD VALUE IF NOT EXISTS 'REVOKED' AFTER 'RETRACTED';
