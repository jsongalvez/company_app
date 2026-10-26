-- The probe's own branch and staff user, nothing else: it can reach the probe branch only.
-- Safe to rerun. Usage: psql -v ON_ERROR_STOP=1 -v pass_hash="'<bcrypt>'" -f seed-probe.sql
INSERT INTO branch (name, branch_type) VALUES ('probe', 'CLINIC') ON CONFLICT DO NOTHING;
INSERT INTO app_user (username, password_hash, display_name, email)
  VALUES ('probe', :pass_hash, 'Latency probe', 'probe@example.com') ON CONFLICT DO NOTHING;
INSERT INTO session_base_rate (set_by, branch_id, session_type, rate, effective_from, effective_until)
  SELECT u.id, b.id, 'REGULAR', 1.00, now(), now() + interval '10 years'
  FROM app_user u, branch b WHERE u.username = 'probe' AND b.name = 'probe'
    AND NOT EXISTS (SELECT 1 FROM session_base_rate x WHERE x.branch_id = b.id);
INSERT INTO user_capability (user_id, capability_id, context_type, context_id, source_type, source_id)
  SELECT u.id, c.id, 'BRANCH', b.id, 'SYSTEM', b.id
  FROM app_user u, branch b, capability c
  WHERE u.username = 'probe' AND b.name = 'probe' AND c.code IN ('VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'VOID_SESSION')
    AND NOT EXISTS (SELECT 1 FROM user_capability x WHERE x.user_id = u.id AND x.capability_id = c.id);
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
  SELECT u.id, b.id, 0, u.id FROM app_user u, branch b
  WHERE u.username = 'probe' AND b.name = 'probe'
    AND NOT EXISTS (SELECT 1 FROM user_branch_assignment x WHERE x.user_id = u.id);
SELECT u.username, b.name AS branch FROM app_user u, branch b WHERE u.username = 'probe' AND b.name = 'probe';
