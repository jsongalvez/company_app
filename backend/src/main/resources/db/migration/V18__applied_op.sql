-- The answer to each queued save the server took, by the phone's own id for it, so a resend
-- gets the same answer without running twice (`OpLog`). Status 0: the save is still running,
-- so a resend over bad wifi waits instead of running beside it. Kept 14 days.
CREATE TABLE applied_op (
    user_id    UUID        NOT NULL REFERENCES app_user(id),
    op_id      TEXT        NOT NULL,
    status     INTEGER     NOT NULL,
    body       TEXT        NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, op_id)
);
CREATE INDEX idx_applied_op_applied_at ON applied_op (applied_at);
