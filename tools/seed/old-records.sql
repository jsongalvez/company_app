-- Old records for record-lists.e2e.ts: more than one page of feedback reports, read alerts and
-- change-history entries, all well in the past, so the test can check each list opens on the newest
-- records without a date and reaches the oldest only through its Show older (or Show more) button.
-- Run by CI (browser.yml) and `tools/verify/verify.sh up`, after DevMain has seeded owner and staff.
INSERT INTO user_feedback (id, user_id, kind, message, status, created_at)
SELECT gen_random_uuid(), u.id, 'PROBLEM', 'Older report ' || lpad(i::text, 3, '0'), 'NEW', now() - (40 + i) * interval '1 day'
FROM app_user u, generate_series(1, 150) i
WHERE u.username = 'staff';

INSERT INTO notification (user_id, branch_id, is_read, read_at, created_at, message, dedup_key)
SELECT u.id, '00000000-0000-4000-8000-000000000001', true, now() - (40 + i) * interval '1 day',
       now() - (40 + i) * interval '1 day', 'Older alert ' || lpad(i::text, 3, '0'), 'seed-older-alert-' || i
FROM app_user u, generate_series(1, 60) i
WHERE u.username = 'owner'
ON CONFLICT (dedup_key, user_id) DO NOTHING;

INSERT INTO audit_log (table_name, record_id, action, changed_by, changed_at, new_value)
SELECT 'client', gen_random_uuid(), 'INSERT', u.id, now() - (400 + i) * interval '1 day',
       jsonb_build_object('notes', 'Older change ' || lpad(i::text, 3, '0'))
FROM app_user u, generate_series(1, 120) i
WHERE u.username = 'staff';
