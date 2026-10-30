-- A client waits in one queue per day, not one forever (ADR-0026, feedback d6cc83cf).
-- The same-day check across branches runs in SessionRepository under the client row lock;
-- this index keeps one Waiting session per client per branch day.
DROP INDEX IF EXISTS idx_client_one_pending_session;
CREATE UNIQUE INDEX idx_client_one_pending_session_per_day
    ON session (client_id, branch_day_id)
    WHERE session_status = 'PENDING' AND is_voided = FALSE;
