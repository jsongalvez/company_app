-- #356 — notification storage widening: one person holds many notifications.
-- A repeat event inserts a new row rather than vanishing (the (session_id, user_id)
-- uniqueness moves into the reminder write path), and session_id becomes nullable so
-- non-session events can exist (relief notifications land in #358).
ALTER TABLE notification ALTER COLUMN session_id DROP NOT NULL;

DROP INDEX IF EXISTS idx_notification_unique;
