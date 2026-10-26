#!/usr/bin/env bash
# Seed a verify stack's throwaway database for load tests: B extra branches (loadbranch01..) with the
# fixture branch's base rates, and N staff users (load001..) cloned from the seeded "staff" login
# (same password), spread over the branches. Usage: VERIFY_PORT=18185 seed.sh 40 8
set -euo pipefail
N=${1:?users}
B=${2:?branches}
PORT=${VERIFY_PORT:-18185}
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
set -a; . "$ROOT/.env"; set +a
export PGPASSWORD=$POSTGRES_PASSWORD
psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "verify_$PORT" -v ON_ERROR_STOP=1 -q <<SQL
CREATE TEMP TABLE staff AS SELECT * FROM app_user WHERE username = 'staff';
INSERT INTO branch (name, branch_type)
  SELECT format('loadbranch%s', lpad(g::text, 2, '0')), 'CLINIC' FROM generate_series(1, $B) g ON CONFLICT DO NOTHING;
INSERT INTO session_base_rate (set_by, branch_id, session_type, rate, effective_from, effective_until)
  SELECT r.set_by, b.id, r.session_type, r.rate, r.effective_from, r.effective_until
  FROM branch b, session_base_rate r
  WHERE b.name LIKE 'loadbranch%' AND r.branch_id = '00000000-0000-4000-8000-000000000001'
    AND NOT EXISTS (SELECT 1 FROM session_base_rate x WHERE x.branch_id = b.id);
CREATE TEMP TABLE placed AS
  SELECT g AS n, format('load%s', lpad(g::text, 3, '0')) AS username,
         (SELECT id FROM branch WHERE name = format('loadbranch%s', lpad(((g - 1) % $B + 1)::text, 2, '0'))) AS branch_id
  FROM generate_series(1, $N) g;
INSERT INTO app_user (username, password_hash, display_name, email)
  SELECT p.username, s.password_hash, 'Load ' || p.n, p.username || '@example.com' FROM placed p, staff s ON CONFLICT DO NOTHING;
INSERT INTO user_capability (user_id, capability_id, context_type, context_id, source_type, source_id)
  SELECT u.id, c.capability_id, c.context_type, p.branch_id, c.source_type, p.branch_id
  FROM placed p JOIN app_user u ON u.username = p.username, user_capability c, staff s
  WHERE c.user_id = s.id AND NOT EXISTS (SELECT 1 FROM user_capability x WHERE x.user_id = u.id);
INSERT INTO user_branch_assignment (user_id, branch_id, slot, assigned_by)
  SELECT u.id, p.branch_id, 0, u.id FROM placed p JOIN app_user u ON u.username = p.username
  WHERE NOT EXISTS (SELECT 1 FROM user_branch_assignment x WHERE x.user_id = u.id);
SELECT count(*) AS load_users FROM app_user WHERE username LIKE 'load%';
SELECT count(*) AS load_branches FROM branch WHERE name LIKE 'loadbranch%';
SQL
