-- A pending relief invite for the relief worker, from the dev seed's staff user at the fixture branch,
-- twenty business days out, so roles.e2e.ts can accept one without signing in as the sender first.
-- Run by CI (browser.yml) and `tools/verify/verify.sh up`, after DevMain has seeded staff and relief.
WITH d AS (
  INSERT INTO branch_day (branch_id, date)
  VALUES ('00000000-0000-4000-8000-000000000001',
          ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date + 20)
  ON CONFLICT (branch_id, date) DO UPDATE SET date = EXCLUDED.date
  RETURNING id
)
INSERT INTO relief_invite (branch_day_id, invited_by, invitee)
SELECT d.id, s.id, r.id
FROM d, app_user s, app_user r
WHERE s.username = 'staff' AND r.username = 'relief';
