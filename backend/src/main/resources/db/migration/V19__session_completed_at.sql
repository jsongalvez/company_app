-- When a session was marked done, so Today's log can order by it. A mark sent later holds when it
-- happened on the phone. Undoing the mark clears it. Sessions done before this migration have none.
ALTER TABLE session ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE session ADD CONSTRAINT completed_at_only_when_done
    CHECK (completed_at IS NULL OR session_status = 'COMPLETED');
