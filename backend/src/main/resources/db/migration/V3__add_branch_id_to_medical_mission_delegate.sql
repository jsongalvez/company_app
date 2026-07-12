ALTER TABLE medical_mission_delegate
    ADD COLUMN branch_id UUID NOT NULL REFERENCES branch(id);
