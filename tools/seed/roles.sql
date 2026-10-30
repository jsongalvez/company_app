-- One user per role the app is used with, for the browser tests in roles.e2e.ts. Each user holds
-- only its role (no direct grants, unlike the dev seed's owner and staff) and works its own branch,
-- so what a test sees is what a real person with that role sees, and one role's remittance can't
-- close another test's day. Run by CI (browser.yml) and `tools/verify/verify.sh up`, after DevMain
-- has seeded `staff`: every role user signs in with staff's password (staff-dev-pass).
WITH r(role, n) AS (
  VALUES ('SUPERUSER', 1), ('OWNER', 2), ('MANAGER', 3), ('COORDINATOR', 4), ('PRACTITIONER', 5)
), b AS (
  INSERT INTO branch (id, name)
  SELECT ('00000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid, initcap(role) || ' Branch' FROM r
  RETURNING id, name
), u AS (
  INSERT INTO app_user (username, password_hash, email, display_name)
  SELECT 'role-' || lower(role), (SELECT password_hash FROM app_user WHERE username = 'staff'),
         'role-' || lower(role) || '@example.com', 'Role ' || initcap(role)
  FROM r
  RETURNING id, username
), ur AS (
  INSERT INTO user_role (user_id, role_id)
  SELECT u.id, role.id FROM u JOIN r ON u.username = 'role-' || lower(r.role) JOIN role ON role.name = r.role
)
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
SELECT u.id, b.id, 1, u.id
FROM u JOIN r ON u.username = 'role-' || lower(r.role) JOIN b ON b.name = initcap(r.role) || ' Branch';

-- An accountant, who belongs to no branch (owed #23: "They can't be a member of any branch"), for
-- commission-privacy.e2e.ts. Signs in with staff's password like the others.
WITH u AS (
  INSERT INTO app_user (username, password_hash, email, display_name)
  SELECT 'role-accountant', password_hash, 'role-accountant@example.com', 'Role Accountant'
  FROM app_user WHERE username = 'staff'
  RETURNING id
)
INSERT INTO user_role (user_id, role_id)
SELECT u.id, role.id FROM u, role WHERE role.name = 'ACCOUNTANT';

-- The five default rates the app gives every branch it makes (BranchService.create).
INSERT INTO session_base_rate (set_by, branch_id, session_type, rate, effective_until)
SELECT u.id, b.id, t.session_type::session_type, t.rate, TIMESTAMPTZ '9999-12-31 23:59:59+00'
FROM branch b
JOIN app_user u ON u.username = 'role-' || lower(split_part(b.name, ' ', 1))
CROSS JOIN (VALUES ('REGULAR', 2500.00), ('SECOND_SESSION', 2000.00), ('SUBSEQUENT', 1500.00),
                   ('PROVINCIAL_FIRST', 3500.00), ('MEDICAL_MISSION', 0.00)) AS t(session_type, rate)
WHERE b.name LIKE '% Branch' AND b.id::text LIKE '00000000-0000-4000-8000-0000000001%';

-- A product in stock at every role branch, so each role can record a sale without restocking first.
WITH c AS (
  INSERT INTO product_category (name) VALUES ('Role test oils') RETURNING id
), p AS (
  INSERT INTO product (name, product_category_id, unit_price, commission_amount)
  SELECT 'Role test oil', c.id, 100, 10 FROM c RETURNING id
)
INSERT INTO branch_inventory (branch_id, product_id, current_stock, unit_price, commission_amount)
SELECT b.id, p.id, 50, 100, 10
FROM branch b, p
WHERE b.id::text LIKE '00000000-0000-4000-8000-0000000001%';

-- A remitted day three business days back at the Practitioner Branch, with one session, for
-- branch-edit.e2e.ts: a member sees no change controls on a remitted day.
WITH c AS (
  INSERT INTO client (first_name, last_name, gender, age)
  VALUES ('Remit', 'Dayclient', 'F', 40) RETURNING id
), d AS (
  INSERT INTO branch_day (branch_id, date, status)
  VALUES ('00000000-0000-4000-8000-000000000105',
          ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date - 3, 'REMITTED')
  ON CONFLICT (branch_id, date) DO UPDATE SET status = 'REMITTED'
  RETURNING id
)
INSERT INTO session (client_id, branch_day_id, session_type, is_walk_in, base_price, final_price)
SELECT c.id, d.id, 'REGULAR', true, 500, 500 FROM c, d;

-- Yesterday at the Practitioner Branch, so branch-edit.e2e.ts can add and fix an earlier day's expense.
INSERT INTO branch_day (branch_id, date)
VALUES ('00000000-0000-4000-8000-000000000105', ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date - 1)
ON CONFLICT (branch_id, date) DO NOTHING;

-- Yesterday at the Practitioner Branch, a session with one product sale, for sale-edit.e2e.ts:
-- a member changes, deletes and reads the history of a sale from its session's page.
WITH c AS (
  INSERT INTO client (first_name, last_name, gender, age)
  VALUES ('Sale', 'Dayclient', 'F', 40) RETURNING id
), d AS (
  SELECT id FROM branch_day
  WHERE branch_id = '00000000-0000-4000-8000-000000000105'
    AND date = ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date - 1
), s AS (
  INSERT INTO session (client_id, branch_day_id, session_type, is_walk_in, base_price, final_price)
  SELECT c.id, d.id, 'REGULAR', true, 500, 500 FROM c, d RETURNING id, branch_day_id
)
INSERT INTO product_sale (branch_day_id, session_id, is_walk_in, product_id, product_name, handled_by,
                          quantity, unit_price_at_time, total_amount_at_time, commission_amount_at_time)
SELECT s.branch_day_id, s.id, false, p.id, p.name, u.id, 1, 100, 100, 10
FROM s, product p, app_user u
WHERE p.name = 'Role test oil' AND u.username = 'role-practitioner';

-- A relief worker with no home branch, granted today at the Practitioner Branch (owed #23: "relief has
-- same access as branch members only for that granted day"), and a walk-in sale there today, for
-- view-only.e2e.ts: relief sees the day's sales list.
WITH u AS (
  INSERT INTO app_user (username, password_hash, email, display_name)
  SELECT 'role-relief', password_hash, 'role-relief@example.com', 'Role Relief'
  FROM app_user WHERE username = 'staff'
  RETURNING id
), r AS (
  INSERT INTO user_role (user_id, role_id) SELECT u.id, role.id FROM u, role WHERE role.name = 'PRACTITIONER'
), d AS (
  INSERT INTO branch_day (branch_id, date)
  VALUES ('00000000-0000-4000-8000-000000000105', ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date)
  ON CONFLICT (branch_id, date) DO UPDATE SET date = EXCLUDED.date
  RETURNING id
), s AS (
  INSERT INTO product_sale (branch_day_id, is_walk_in, product_id, product_name, handled_by,
                            quantity, unit_price_at_time, total_amount_at_time, commission_amount_at_time)
  SELECT d.id, true, p.id, p.name, h.id, 3, 100, 300, 10
  FROM d, product p, app_user h
  WHERE p.name = 'Role test oil' AND h.username = 'role-practitioner'
)
INSERT INTO user_capability (user_id, capability_id, context_type, context_id, source_type, source_id)
SELECT u.id, c.id, 'BRANCH_DAY', d.id, 'RELIEF_ACCESS', u.id
FROM u, d, capability c
WHERE c.code = 'EDIT_BRANCH_DATA';
