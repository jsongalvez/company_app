-- Relief is a fact of its own: who works which branch day, granted by which request or invite.
CREATE TABLE relief_day (
    user_id       UUID        NOT NULL REFERENCES app_user(id),
    branch_day_id UUID        NOT NULL REFERENCES branch_day(id),
    source_id     UUID        NOT NULL,
    valid_to      TIMESTAMPTZ,
    PRIMARY KEY (user_id, branch_day_id, source_id)
);
CREATE INDEX idx_relief_day_day ON relief_day (branch_day_id);

INSERT INTO relief_day (user_id, branch_day_id, source_id, valid_to)
SELECT uc.user_id, uc.context_id, uc.source_id, uc.valid_to
FROM user_capability uc
JOIN capability c ON c.id = uc.capability_id
WHERE uc.context_type = 'BRANCH_DAY' AND c.code = 'EDIT_BRANCH_DATA'
ON CONFLICT DO NOTHING;

DELETE FROM user_capability WHERE context_type = 'BRANCH_DAY';

ALTER TABLE user_capability ADD CONSTRAINT no_day_grants CHECK (context_type <> 'BRANCH_DAY');
