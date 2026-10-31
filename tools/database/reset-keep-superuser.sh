#!/usr/bin/env bash
# Starts a database over, keeping only the Superuser login(s).
#   wipe <dump>       drop every table, view and Flyway row in schema public, so the
#                     next app start runs the migrations on an empty schema
#   bootstrap <dump>  after the app has migrated: copy each Superuser's account row
#                     (same id, username, email, password hash) and role from the dump
# <dump> is a pg_dump -Fc file made by prod-snapshot.sh, the only way back. Nothing
# personal is printed: account rows are piped from the dump straight into the database.
set -euo pipefail

DB_CONTAINER=${DB_CONTAINER:-0jpjzfkecdudofqzcijsdyrc}
DB=${DB:-company_app}
cmd=${1:?usage: $0 wipe|bootstrap <dump>}
dump=${2:?usage: $0 wipe|bootstrap <dump>}
[[ -s "$dump" ]] || { echo "no dump at $dump" >&2; exit 1; }

pg() { sudo docker exec -i "$DB_CONTAINER" sh -c "$1"; }
psql_db() { pg "psql -U \"\$POSTGRES_USER\" -d $1 -v ON_ERROR_STOP=1 -Atq"; }

toc=$(pg "pg_restore -l" <"$dump")
grep -q 'TABLE DATA public app_user ' <<<"$toc" || { echo "$dump holds no app_user rows; refusing" >&2; exit 1; }

case "$cmd" in
wipe)
  psql_db "$DB" <<'SQL'
SET client_min_messages = warning;
BEGIN;
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
COMMIT;
SELECT 'tables left: ' || count(*) FROM pg_tables WHERE schemaname = 'public';
SQL
  ;;
bootstrap)
  scratch="superuser_src_$$"
  trap 'pg "dropdb -U \"\$POSTGRES_USER\" --if-exists $scratch" >/dev/null 2>&1 || true' EXIT
  pg "createdb -U \"\$POSTGRES_USER\" $scratch"
  pg "pg_restore -U \"\$POSTGRES_USER\" -d $scratch --no-owner --no-acl --no-tablespaces --exit-on-error" <"$dump"
  [[ "$(psql_db "$DB" <<<"SELECT count(*) FROM app_user")" == 0 ]] \
    || { echo "app_user is not empty; bootstrap runs once, on a fresh schema" >&2; exit 1; }
  {
    echo "BEGIN;"
    psql_db "$scratch" <<'SQL'
SELECT format(
  'INSERT INTO app_user (id, username, password_hash, status, email, display_name, created_at, credential_version) '
  'VALUES (%L, %L, %L, %L, %L, %L, %L, %L);',
  u.id, u.username, u.password_hash, u.status, u.email, u.display_name, u.created_at, u.credential_version)
  || format(' INSERT INTO user_role (user_id, role_id) SELECT %L, id FROM role WHERE name = ''SUPERUSER'';', u.id)
FROM app_user u
WHERE u.status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.name = 'SUPERUSER');
SQL
    echo "COMMIT;"
  } | psql_db "$DB" >/dev/null
  psql_db "$DB" <<'SQL'
SELECT 'superusers: ' || count(*) FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE r.name = 'SUPERUSER';
SELECT 'accounts: ' || count(*) FROM app_user;
SQL
  ;;
*) echo "unknown command $cmd" >&2; exit 2 ;;
esac
