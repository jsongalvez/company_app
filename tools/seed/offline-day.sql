-- For offline-day.e2e.ts: a practitioner and a coordinator who each work two branches of their own,
-- so a day's work done online at one and offline (or on bad wifi) at the other can be compared total
-- for total. Run after DevMain has seeded `staff`: both sign in with staff's password (staff-dev-pass).
WITH b(id, name) AS (
  VALUES ('00000000-0000-4000-8000-000000000201'::uuid, 'Online P Branch'),
         ('00000000-0000-4000-8000-000000000202'::uuid, 'Offline P Branch'),
         ('00000000-0000-4000-8000-000000000203'::uuid, 'Online C Branch'),
         ('00000000-0000-4000-8000-000000000204'::uuid, 'Offline C Branch')
)
INSERT INTO branch (id, name) SELECT id, name FROM b;

WITH r(username, role, branches) AS (
  VALUES ('offline-practitioner', 'PRACTITIONER', ARRAY['00000000-0000-4000-8000-000000000201', '00000000-0000-4000-8000-000000000202']),
         ('offline-coordinator', 'COORDINATOR', ARRAY['00000000-0000-4000-8000-000000000203', '00000000-0000-4000-8000-000000000204'])
), u AS (
  INSERT INTO app_user (username, password_hash, email, display_name)
  SELECT r.username, (SELECT password_hash FROM app_user WHERE username = 'staff'), r.username || '@example.com',
         'Offline ' || initcap(lower(r.role))
  FROM r
  RETURNING id, username
), ur AS (
  INSERT INTO user_role (user_id, role_id)
  SELECT u.id, role.id FROM u JOIN r ON r.username = u.username JOIN role ON role.name = r.role
)
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
SELECT u.id, unnest(r.branches)::uuid, 1, u.id FROM u JOIN r ON r.username = u.username;

INSERT INTO session_base_rate (set_by, branch_id, session_type, rate, effective_until)
SELECT u.id, b.id, t.session_type::session_type, t.rate, TIMESTAMPTZ '9999-12-31 23:59:59+00'
FROM branch b
JOIN app_user u ON u.username = 'staff'
CROSS JOIN (VALUES ('REGULAR', 2500.00), ('SECOND_SESSION', 2000.00), ('SUBSEQUENT', 1500.00),
                   ('PROVINCIAL_FIRST', 3500.00), ('MEDICAL_MISSION', 0.00)) AS t(session_type, rate)
WHERE b.id::text LIKE '00000000-0000-4000-8000-0000000002%';

WITH c AS (
  INSERT INTO product_category (name) VALUES ('Offline test oils') RETURNING id
), p AS (
  INSERT INTO product (name, product_category_id, unit_price, commission_amount)
  SELECT 'Offline test oil', c.id, 100, 10 FROM c RETURNING id
)
INSERT INTO branch_inventory (branch_id, product_id, current_stock, unit_price, commission_amount)
SELECT b.id, p.id, 50, 100, 10
FROM branch b, p
WHERE b.id::text LIKE '00000000-0000-4000-8000-0000000002%';

-- People the coordinator pays, one per branch and test: pay is one per person and paying day.
WITH p(username, display_name, branch_id) AS (
  VALUES ('payee-on-nosignal', 'Payee no signal', '00000000-0000-4000-8000-000000000203'::uuid),
         ('payee-off-nosignal', 'Payee no signal', '00000000-0000-4000-8000-000000000204'::uuid),
         ('payee-on-badwifi', 'Payee bad wifi', '00000000-0000-4000-8000-000000000203'::uuid),
         ('payee-off-badwifi', 'Payee bad wifi', '00000000-0000-4000-8000-000000000204'::uuid)
), u AS (
  INSERT INTO app_user (username, password_hash, email, display_name)
  SELECT p.username, (SELECT password_hash FROM app_user WHERE username = 'staff'), p.username || '@example.com', p.display_name
  FROM p
  RETURNING id, username
), ur AS (
  INSERT INTO user_role (user_id, role_id)
  SELECT u.id, role.id FROM u JOIN role ON role.name = 'PRACTITIONER'
)
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
SELECT u.id, p.branch_id, 1, u.id FROM u JOIN p ON p.username = u.username;
