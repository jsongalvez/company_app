-- A helper seat is a fact of its own: the medical_mission_delegate row, read by the policy. Grant rows from a seat go.
DELETE FROM user_capability WHERE source_type = 'MEDICAL_MISSION_DELEGATE';

ALTER TABLE user_capability ADD CONSTRAINT no_helper_grants CHECK (source_type <> 'MEDICAL_MISSION_DELEGATE');

CREATE INDEX idx_medical_mission_delegate_open ON medical_mission_delegate (target_user) WHERE ended_at IS NULL;
