-- =============================================================================
-- V6__anonymized_client_audit_redaction.sql — retained client audit PII redaction (#524)
--
-- Anonymization nullifies the identifying client columns, but
-- application-managed audit payloads kept the removed first/last names —
-- including the anonymization event's own before-image. This backfills those
-- retained payloads for already-anonymized clients: identifying firstName /
-- lastName values become "[redacted]" (AuditValues.REDACTED — the same marker
-- the anonymize command writes for new anonymizations).
--
-- Scope is deliberately narrow: only audit_log rows for the client table whose
-- record is anonymized (deleted_at IS NOT NULL); only the two name keys;
-- values already "null" or "[redacted]" are left alone so cleared-state
-- history keeps its shape. Event identity (actor, timestamp, action, record,
-- changed-field keys), demographic columns (gender, age), session linkage, and
-- every non-client table — including remittance financial snapshots — are
-- untouched. No rows are deleted. This is the single documented exception to
-- audit-payload immutability; see redactClientNamesInTransaction (#524 field
-- policy) for the matching live-write rule #525 must extend.
-- =============================================================================

-- Backfill old_value payloads (create/update before-images, including the
-- anonymization event's own before-image).
UPDATE audit_log
SET old_value =
    CASE WHEN old_value ->> 'lastName' NOT IN ('null', '[redacted]')
        THEN jsonb_set(
            CASE WHEN old_value ->> 'firstName' NOT IN ('null', '[redacted]')
                THEN jsonb_set(old_value, '{firstName}', '"[redacted]"'::jsonb)
                ELSE old_value END,
            '{lastName}', '"[redacted]"'::jsonb)
        ELSE CASE WHEN old_value ->> 'firstName' NOT IN ('null', '[redacted]')
                THEN jsonb_set(old_value, '{firstName}', '"[redacted]"'::jsonb)
                ELSE old_value END
    END
WHERE table_name = 'client'
  AND record_id IN (SELECT id FROM client WHERE deleted_at IS NOT NULL)
  AND (
    old_value ->> 'firstName' NOT IN ('null', '[redacted]')
    OR old_value ->> 'lastName' NOT IN ('null', '[redacted]')
  );

-- Backfill new_value payloads (create/insert images and rename after-images).
UPDATE audit_log
SET new_value =
    CASE WHEN new_value ->> 'lastName' NOT IN ('null', '[redacted]')
        THEN jsonb_set(
            CASE WHEN new_value ->> 'firstName' NOT IN ('null', '[redacted]')
                THEN jsonb_set(new_value, '{firstName}', '"[redacted]"'::jsonb)
                ELSE new_value END,
            '{lastName}', '"[redacted]"'::jsonb)
        ELSE CASE WHEN new_value ->> 'firstName' NOT IN ('null', '[redacted]')
                THEN jsonb_set(new_value, '{firstName}', '"[redacted]"'::jsonb)
                ELSE new_value END
    END
WHERE table_name = 'client'
  AND record_id IN (SELECT id FROM client WHERE deleted_at IS NOT NULL)
  AND (
    new_value ->> 'firstName' NOT IN ('null', '[redacted]')
    OR new_value ->> 'lastName' NOT IN ('null', '[redacted]')
  );
