#!/usr/bin/env bash
# Apply one migration to a scratch copy of a prod dump, run a check query, then drop the copy.
# Live prod is never written. Usage:
#   tools/database/migration-dryrun.sh MIGRATION.sql 'SELECT ... check ...' [DUMP]
# DUMP defaults to the newest file in $OUT_DIR. Prints the check's rows; exits non-zero when the migration fails.
set -euo pipefail

DB_CONTAINER=${DB_CONTAINER:-0jpjzfkecdudofqzcijsdyrc}
OUT_DIR=${OUT_DIR:-/mnt/data/backups}
migration=${1:?migration .sql}
check=${2:?check query}
dump=${3:-$(ls -t "$OUT_DIR"/*.dump | head -1)}
scratch="dryrun_$$"

pg() { sudo docker exec -i "$DB_CONTAINER" sh -c "$1"; }
trap 'pg "dropdb -U \"\$POSTGRES_USER\" --if-exists $scratch" >/dev/null 2>&1 || true' EXIT

echo "dump: $dump" >&2
pg "createdb -U \"\$POSTGRES_USER\" $scratch"
pg "pg_restore -U \"\$POSTGRES_USER\" -d $scratch --no-owner --no-acl --no-tablespaces --exit-on-error" <"$dump"
pg "psql -U \"\$POSTGRES_USER\" -d $scratch -v ON_ERROR_STOP=1 -q" <"$migration"
pg "psql -U \"\$POSTGRES_USER\" -d $scratch -At -c \"$check\""
