-- CR-019: Fix rate overlap constraint to use half-open range [)
-- With '[]', deactivating a rate at time T and inserting a new one at T
-- creates overlapping ranges: [old_from, T] and [T, far_future] share T.
-- With '[)', the ranges become [old_from, T) and [T, far_future) — no overlap.
ALTER TABLE session_base_rate DROP CONSTRAINT IF EXISTS no_rate_overlap;

ALTER TABLE session_base_rate ADD CONSTRAINT no_rate_overlap EXCLUDE USING gist (
    branch_id    WITH =,
    session_type WITH =,
    tstzrange(effective_from, effective_until, '[)') WITH &&
);
