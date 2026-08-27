-- #425 — a voided PENDING session is not an active pending visit. Store the
-- void state on session so PostgreSQL can enforce the rule in a partial index;
-- partial-index predicates cannot query active_session_voids.
ALTER TABLE session
    ADD COLUMN is_voided BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE session
SET is_voided = TRUE
WHERE id IN (
    SELECT session_id
    FROM active_session_voids
);

DROP INDEX idx_client_one_pending_session;

CREATE UNIQUE INDEX idx_client_one_pending_session
    ON session (client_id)
    WHERE session_status = 'PENDING' AND is_voided = FALSE;
