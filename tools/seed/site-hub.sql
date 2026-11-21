-- For site-hub.e2e.ts: five practitioners who share two branches. The same work is done at Desk H
-- online and at Site H with the cloud cut off and a site hub on the hotspot, and the two days'
-- totals are compared. Run after offline-day.sql (its product) and after DevMain has seeded `staff`:
-- each signs in with staff's password (staff-dev-pass).
INSERT INTO branch (id, name) VALUES
  ('00000000-0000-4000-8000-000000000301', 'Desk H Branch'),
  ('00000000-0000-4000-8000-000000000302', 'Site H Branch');

WITH r(username, display_name) AS (
  VALUES ('hub-a', 'Hub Ana'), ('hub-b', 'Hub Ben'), ('hub-c', 'Hub Cora'), ('hub-d', 'Hub Dan'), ('hub-e', 'Hub Eva')
), u AS (
  INSERT INTO app_user (username, password_hash, email, display_name)
  SELECT r.username, (SELECT password_hash FROM app_user WHERE username = 'staff'), r.username || '@example.com', r.display_name
  FROM r
  RETURNING id
), ur AS (
  INSERT INTO user_role (user_id, role_id) SELECT u.id, role.id FROM u JOIN role ON role.name = 'PRACTITIONER'
)
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
SELECT u.id, b.id, 1, u.id FROM u CROSS JOIN (VALUES ('00000000-0000-4000-8000-000000000301'::uuid), ('00000000-0000-4000-8000-000000000302'::uuid)) AS b(id);

INSERT INTO session_base_rate (set_by, branch_id, session_type, rate, effective_until)
SELECT u.id, b.id, t.session_type::session_type, t.rate, TIMESTAMPTZ '9999-12-31 23:59:59+00'
FROM branch b
JOIN app_user u ON u.username = 'staff'
CROSS JOIN (VALUES ('REGULAR', 2500.00), ('SECOND_SESSION', 2000.00), ('SUBSEQUENT', 1500.00),
                   ('PROVINCIAL_FIRST', 3500.00), ('MEDICAL_MISSION', 0.00)) AS t(session_type, rate)
WHERE b.id::text LIKE '00000000-0000-4000-8000-0000000003%';

INSERT INTO branch_inventory (branch_id, product_id, current_stock, unit_price, commission_amount)
SELECT b.id, p.id, 50, 100, 10
FROM branch b, product p
WHERE b.id::text LIKE '00000000-0000-4000-8000-0000000003%' AND p.name = 'Offline test oil';
