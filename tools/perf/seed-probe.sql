-- The probe's own branch and staff user, nothing else: it can reach the probe branch only.
-- The branch is hidden (V5), so people never see it in branch lists, the all-branches export or the audit.
-- Safe to rerun. Run it through seed-probe.sh, which sets pass_hash.
INSERT INTO branch (name, branch_type, hidden) VALUES ('probe', 'CLINIC', true)
  ON CONFLICT (branch_type, name) DO UPDATE SET hidden = true WHERE branch.hidden IS DISTINCT FROM true;
INSERT INTO app_user (username, password_hash, display_name, email)
  VALUES ('probe', :'pass_hash', 'Latency probe', 'probe@example.com') ON CONFLICT DO NOTHING;
-- Every session type gets a rate, as on a branch made in the app (BranchService.create): with REGULAR
-- only, opening New session for a returning client got 400 "No base rate".
INSERT INTO session_base_rate (set_by, branch_id, session_type, rate, effective_from, effective_until)
  SELECT u.id, b.id, t.session_type::session_type, 1.00, now(), now() + interval '10 years'
  FROM app_user u, branch b,
       (VALUES ('REGULAR'), ('SECOND_SESSION'), ('SUBSEQUENT'), ('PROVINCIAL_FIRST'), ('MEDICAL_MISSION')) AS t(session_type)
  WHERE u.username = 'probe' AND b.name = 'probe'
    AND NOT EXISTS (SELECT 1 FROM session_base_rate x
                    WHERE x.branch_id = b.id AND x.session_type = t.session_type::session_type AND x.effective_until > now());
INSERT INTO user_role (user_id, role_id)
  SELECT u.id, r.id FROM app_user u, role r
  WHERE u.username = 'probe' AND r.name = 'PRACTITIONER'
    AND NOT EXISTS (SELECT 1 FROM user_role x WHERE x.user_id = u.id AND x.role_id = r.id);
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
  SELECT u.id, b.id, 0, u.id FROM app_user u, branch b
  WHERE u.username = 'probe' AND b.name = 'probe'
    AND NOT EXISTS (SELECT 1 FROM user_branch_assignment x WHERE x.user_id = u.id);
SELECT u.username, b.name AS branch, b.hidden FROM app_user u, branch b WHERE u.username = 'probe' AND b.name = 'probe';
