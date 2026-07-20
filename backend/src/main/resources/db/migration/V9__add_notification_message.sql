-- =============================================================================
-- V9__add_notification_message.sql
-- Add message column to notification table per design spec §20.
-- CR-027: notification table was missing message TEXT NOT NULL.
-- =============================================================================

ALTER TABLE notification
    ADD COLUMN message TEXT NOT NULL DEFAULT '';

-- Default only needed for existing rows. New rows always get a message from
-- the application (scheduler provides "You have an upcoming appointment on …").
ALTER TABLE notification
    ALTER COLUMN message DROP DEFAULT;
