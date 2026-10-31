#!/usr/bin/env bash
# Full dump of the live database (telemetry included) to the data disk, then
# proof it restores: a scratch database is built from the dump and every
# table's exact row count must match the live one.
# Usage: tools/database/prod-snapshot.sh [label]   -> prints the dump path
set -euo pipefail

DB_CONTAINER=${DB_CONTAINER:-0jpjzfkecdudofqzcijsdyrc}
DB=${DB:-company_app}
OUT_DIR=${OUT_DIR:-/mnt/data/backups}
label=${1:-snapshot}
scratch="restore_check_$$"
dump="$OUT_DIR/${DB}-$(date +%Y-%m-%d_%H%M%S)-${label}.dump"

pg() { sudo docker exec -i "$DB_CONTAINER" sh -c "$1"; }

counts() {
  # Exact counts, one "table|rows" line per table in public, sorted.
  pg "psql -U \"\$POSTGRES_USER\" -d $1 -Atq" <<'SQL'
DO $$ BEGIN CREATE TEMP TABLE c(t text, n bigint); END $$;
DO $$ DECLARE r record; n bigint; BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('SELECT count(*) FROM public.%I', r.tablename) INTO n;
    INSERT INTO c VALUES (r.tablename, n);
  END LOOP; END $$;
SELECT t || '|' || n FROM c ORDER BY t;
SQL
}

sudo mkdir -p "$OUT_DIR"
sudo chown "$(id -u):$(id -g)" "$OUT_DIR"
pg "pg_dump -U \"\$POSTGRES_USER\" -d $DB -Fc" >"$dump"
[[ -s "$dump" ]] || { echo "empty dump: $dump" >&2; exit 1; }
chmod 600 "$dump"

trap 'pg "dropdb -U \"\$POSTGRES_USER\" --if-exists $scratch" >/dev/null 2>&1 || true' EXIT
pg "createdb -U \"\$POSTGRES_USER\" $scratch"
pg "pg_restore -U \"\$POSTGRES_USER\" -d $scratch --no-owner --no-acl --no-tablespaces --exit-on-error" <"$dump"

live=$(counts "$DB"); restored=$(counts "$scratch")
if [[ "$live" != "$restored" ]]; then
  diff <(echo "$live") <(echo "$restored") >&2 || true
  echo "MISMATCH: restored row counts differ from live ($dump)" >&2
  exit 1
fi
echo "ok: $(wc -l <<<"$live") tables, $(awk -F'|' '{s+=$2} END {print s}' <<<"$live") rows match; $(stat -c %s "$dump") bytes" >&2
echo "$dump"
