-- Replaces personal details in a replay copy of the database with stand-ins of the same shape.
-- Ids, amounts, dates, statuses and business names (branches, products, concerns) stay, so screens,
-- totals and permissions behave as they did. Every password becomes the dev owner's, so the replay
-- tool signs in as the person who reported the problem. Run only against a copy (tools/replay/replay.sh).
\set ON_ERROR_STOP on
BEGIN;
UPDATE client SET
    first_name = 'Client',
    last_name = left(md5(id::text), 6),
    middle_name = NULL,
    suffix = NULL,
    phone_number = NULL,
    address = NULL,
    medical_conditions = CASE WHEN medical_conditions IS NULL THEN NULL ELSE 'Redacted' END,
    age = CASE WHEN age IS NULL THEN NULL ELSE greatest(age / 10 * 10, 1) END,
    systolic_bp = NULL,
    diastolic_bp = NULL;
UPDATE app_user SET
    display_name = 'Person ' || left(id::text, 8),
    email = left(id::text, 8) || '@example.invalid',
    username = 'u' || left(id::text, 8),
    password_hash = (SELECT password_hash FROM app_user WHERE username = 'owner')
WHERE username <> 'owner';
DELETE FROM credential_token;
UPDATE session SET remarks = CASE WHEN remarks IS NULL THEN NULL ELSE 'Redacted' END,
    other_concerns = CASE WHEN other_concerns IS NULL THEN NULL ELSE 'Redacted' END;
UPDATE session_practitioner SET remarks = 'Redacted' WHERE remarks IS NOT NULL;
UPDATE session_void SET void_reason = 'Redacted', unvoided_reason = CASE WHEN unvoided_reason IS NULL THEN NULL ELSE 'Redacted' END;
UPDATE expense SET notes = 'Redacted' WHERE notes IS NOT NULL;
UPDATE expense SET deleted_reason = 'Redacted' WHERE deleted_reason IS NOT NULL;
UPDATE inventory_movement SET notes = 'Redacted' WHERE notes IS NOT NULL;
UPDATE compensation SET note = 'Redacted' WHERE note IS NOT NULL;
UPDATE commission_manual_inclusion SET reason = 'Redacted' WHERE reason IS NOT NULL;
UPDATE visit_booking SET note = 'Redacted' WHERE note IS NOT NULL;
UPDATE notification SET message = 'Redacted';
UPDATE audit_log SET
    reason = CASE WHEN reason IS NULL THEN NULL ELSE 'Redacted' END,
    old_value = old_value - ARRAY['firstName','lastName','middleName','suffix','address','phoneNumber','medicalConditions','age','systolicBp','diastolicBp','displayName','email','username','password','note','notes','remarks','otherConcerns','reason','deletedReason','voidReason','unvoidedReason'],
    new_value = new_value - ARRAY['firstName','lastName','middleName','suffix','address','phoneNumber','medicalConditions','age','systolicBp','diastolicBp','displayName','email','username','password','note','notes','remarks','otherConcerns','reason','deletedReason','voidReason','unvoidedReason'];
UPDATE user_feedback SET message = 'Redacted', details = NULL;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM client WHERE first_name <> 'Client' OR phone_number IS NOT NULL OR address IS NOT NULL)
        OR EXISTS (SELECT 1 FROM app_user WHERE username <> 'owner' AND display_name NOT LIKE 'Person %') THEN
        RAISE EXCEPTION 'personal details left after redaction';
    END IF;
END $$;
COMMIT;
