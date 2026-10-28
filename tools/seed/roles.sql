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
