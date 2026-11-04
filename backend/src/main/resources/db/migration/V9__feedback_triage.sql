-- Reports from agents that got tripped up, filed with `app-feedback give`. They have no app user.
CREATE TABLE agent_feedback (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agent      varchar(80) NOT NULL,
    message    text NOT NULL CONSTRAINT agent_feedback_message_length CHECK (char_length(message) BETWEEN 1 AND 4000),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- One verdict per report, written by tools/feedback/triage.py on the VPS. It never changes
-- user_feedback.status: closing a report and starting fix work stay manual.
CREATE TYPE feedback_verdict AS ENUM ('AUTO_FIX', 'FEATURE_REQUEST', 'NOT_A_BUG');
CREATE TABLE feedback_triage (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_id       uuid UNIQUE REFERENCES user_feedback (id) ON DELETE CASCADE,
    agent_feedback_id uuid UNIQUE REFERENCES agent_feedback (id) ON DELETE CASCADE,
    verdict           feedback_verdict NOT NULL,
    reason            varchar(300) NOT NULL,
    classifier        varchar(40) NOT NULL,
    triaged_at        timestamptz NOT NULL DEFAULT now(),
    notified_at       timestamptz,
    CONSTRAINT feedback_triage_one_report CHECK (num_nonnulls(feedback_id, agent_feedback_id) = 1)
);
