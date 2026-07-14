-- V3__create_notification_table.sql
-- Notification table for next-appointment alerts

CREATE TABLE IF NOT EXISTS notification (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES session(id),
    user_id    UUID        NOT NULL REFERENCES app_user(id),
    branch_id  UUID        NOT NULL REFERENCES branch(id),
    is_read    BOOLEAN     NOT NULL DEFAULT false,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One notification per session per user
CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_unique
    ON notification (session_id, user_id);

-- Fast lookup of unread notifications for a user
CREATE INDEX IF NOT EXISTS idx_notification_unread
    ON notification (user_id) WHERE is_read = false;
