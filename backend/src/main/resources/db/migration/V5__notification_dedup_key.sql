-- =============================================================================
-- V5__notification_dedup_key.sql — durable notification idempotency + mailbox indexes (#508)
--
-- Each event occurrence gets a stable key (dedup_key) and delivery is unique per
-- (occurrence, recipient): scheduler retries reuse the occurrence identity while
-- genuinely later events get new identities. Appointment reminders key on
-- session + target appointment date; relief events on event type + source record
-- (revocation's direct invitee notice carries a :direct suffix so both audiences
-- survive even when the invitee is a branch member). The UNIQUE constraint — not
-- the write path's pre-read — is the dedup guarantee, so concurrent batches and
-- job re-runs collapse to one delivery per recipient atomically.
-- Backfill stays unique per row (legacy duplicates predate stable keys and must
-- not violate the new constraint); only post-migration writes share identities.
-- Storage widening (#356) is preserved: distinct occurrences keep distinct keys.
-- =============================================================================

ALTER TABLE notification ADD COLUMN dedup_key VARCHAR(120);

UPDATE notification
SET dedup_key =
    COALESCE(event_type, 'APPT')
    || ':' || COALESCE(source_id::text, session_id::text, id::text)
    || ':' || id::text
WHERE dedup_key IS NULL;

ALTER TABLE notification ALTER COLUMN dedup_key SET NOT NULL;

CREATE UNIQUE INDEX uq_notification_dedup_user
    ON notification (dedup_key, user_id);

-- Recipient/time lookup path: permanent-history keyset pagination over
-- (created_at DESC, id DESC) per recipient.
CREATE INDEX idx_notification_history
    ON notification (user_id, created_at DESC, id DESC);

-- Event lookup path: scheduler/job marker reads and ping-list resolution.
CREATE INDEX idx_notification_event_source
    ON notification (event_type, source_id) WHERE event_type IS NOT NULL;

-- Bearer lookup path: notification-as-authorization session-detail read (#152).
CREATE INDEX idx_notification_session_user
    ON notification (session_id, user_id) WHERE session_id IS NOT NULL;
