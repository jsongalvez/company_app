-- #358 — relief notification events + tap destinations.
--
-- event_type / source_id: correlation for the relief broadcast family. source_id is the
-- causing record (grant_relief_access id or relief_invite id) — polymorphic on purpose,
-- no FK. The expiry notice resolves its "original ping list" (#352 Q3) as the distinct
-- users holding the request's RELIEF_REQUESTED rows.
--
-- target_date: the branch day a non-session notification points at; the client deep-links
-- to the dashboard scoped to (branch_id, target_date). Appointment reminders keep
-- session_id routing and leave all three columns NULL.

ALTER TABLE notification
    ADD COLUMN event_type VARCHAR(50),
    ADD COLUMN source_id UUID,
    ADD COLUMN target_date DATE;
