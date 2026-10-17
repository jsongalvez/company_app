-- Problems and ideas people send from the app ("Send feedback"), kept for triage.
CREATE TABLE user_feedback (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES app_user (id),
    kind        varchar(20) NOT NULL CONSTRAINT user_feedback_kind CHECK (kind IN ('PROBLEM', 'IDEA')),
    message     text NOT NULL CONSTRAINT user_feedback_message_length CHECK (char_length(message) BETWEEN 1 AND 4000),
    page        varchar(300),
    details     text CONSTRAINT user_feedback_details_length CHECK (char_length(details) <= 2000),
    app_version varchar(64),
    status      varchar(20) NOT NULL DEFAULT 'NEW' CONSTRAINT user_feedback_status CHECK (status IN ('NEW', 'DONE')),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX user_feedback_status_created ON user_feedback (status, created_at DESC);
