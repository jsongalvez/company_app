CREATE TYPE relief_invite_status AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED', 'RETRACTED');

CREATE TABLE relief_invite (
    id            uuid                 PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_day_id uuid                 NOT NULL REFERENCES branch_day(id),
    invited_by    uuid                 NOT NULL REFERENCES app_user(id),
    invitee       uuid                 NOT NULL REFERENCES app_user(id),
    status        relief_invite_status NOT NULL DEFAULT 'PENDING',
    responded_at  timestamptz,
    created_at    timestamptz          NOT NULL DEFAULT now()
);

-- The per-person guard (#159 Q1): one live invite per invitee per day. Multiple
-- invitees can hold live invites for the SAME day (the one-grant rule is per
-- person, #159 Q6); DECLINED/RETRACTED rows free the slot (re-invite allowed).
CREATE UNIQUE INDEX idx_one_pending_accepted_invite
    ON relief_invite (invitee, branch_day_id)
    WHERE status IN ('PENDING', 'ACCEPTED');
