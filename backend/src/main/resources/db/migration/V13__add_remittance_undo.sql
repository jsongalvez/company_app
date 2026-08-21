-- #119: remittance undo (48h window) + header PATCH.
-- submitted_at: the exact submission instant the undo window counts from.
-- submitted_date is a calendar DATE (set to today at submit), so the 48h rule
-- cannot be enforced without a timestamp. NULL on legacy rows; undo derives the
-- submission instant from remittance_financial_snapshot.snapshotted_at when
-- submitted_at is missing (pre-V13 SESSION rows) and rejects rows with neither
-- (no production users yet -- no backfill, #121 precedent).
ALTER TABLE remittance ADD COLUMN submitted_at TIMESTAMPTZ;

-- Snapshot immutability carve-out (issue #20 trigger): the snapshot stays
-- write-once -- UPDATE is always blocked; DELETE is allowed only when the
-- parent remittance is already DRAFT, which happens exclusively via the undo
-- endpoint (the only SUBMITTED -> DRAFT transition; the undo transaction
-- updates the remittance before deleting the snapshot, so the trigger sees the
-- DRAFT status).
CREATE OR REPLACE FUNCTION fn_remittance_snapshot_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (
            SELECT 1 FROM remittance WHERE id = OLD.remittance_id AND status = 'DRAFT'
        ) THEN
            RETURN OLD;
        END IF;
    END IF;
    RAISE EXCEPTION
        'remittance_financial_snapshot is immutable. Record for remittance_id % cannot be modified or deleted.', OLD.remittance_id;
END;
$$;
