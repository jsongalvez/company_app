-- A past business day with one walk-in session on the fixture branch, for the browser tests
-- that open past days (past-sessions.e2e.ts). Run by CI (browser.yml) and by `tools/verify/verify.sh up`,
-- so a local run sees the same data as CI. The business day ends at 4 AM Manila, so between
-- midnight and 4 AM calendar yesterday is still today in the app.
WITH c AS (
  INSERT INTO client (first_name, last_name, gender, age)
  VALUES ('Past', 'Dayclient', 'F', 40) RETURNING id
), d AS (
  INSERT INTO branch_day (branch_id, date)
  VALUES ('00000000-0000-4000-8000-000000000001',
          ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date - 1)
  ON CONFLICT (branch_id, date) DO UPDATE SET date = EXCLUDED.date
  RETURNING id
)
INSERT INTO session (client_id, branch_day_id, session_type, is_walk_in, base_price, final_price)
SELECT c.id, d.id, 'REGULAR', true, 500, 500 FROM c, d;
