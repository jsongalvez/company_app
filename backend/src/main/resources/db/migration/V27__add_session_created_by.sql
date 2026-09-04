-- #453 — session idempotency ownership goes transaction-local: the creator is stored
-- on the row (Expense's created_by precedent) so the create seam classifies replays
-- without reading audit history. Nullable + best-effort backfill: pre-migration rows
-- resolve their creator from the INSERT audit row; rows with no audit stay NULL and
-- fail closed on owner mismatch. New rows always write created_by.
ALTER TABLE session ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES app_user(id);

UPDATE session
SET created_by = (
    SELECT changed_by
    FROM audit_log
    WHERE table_name = 'session'
      AND record_id = session.id
      AND action = 'INSERT'
    LIMIT 1
)
WHERE created_by IS NULL;
