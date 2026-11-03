-- The role a helper works under at their mission or tour, picked when they are added. Rows from before this keep a member's rights.
ALTER TABLE medical_mission_delegate
    ADD COLUMN role TEXT NOT NULL DEFAULT 'PRACTITIONER' CHECK (role IN ('PRACTITIONER', 'COORDINATOR'));
