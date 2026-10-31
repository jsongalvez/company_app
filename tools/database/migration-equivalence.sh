#!/usr/bin/env bash
# Proves a reorganized migration set builds the same database as the old chain.
# Builds one scratch database from the migrations at OLD_REF and one from the
# launch set (V1-V4) in the working tree, in a throwaway Postgres container, then
# compares:
#   - pg_dump --schema-only, normalized (no comments, SET lines or blank lines)
#   - reference data by meaning (role names, capability codes, role grants,
#     concern labels) and the row count of every table
#   - with a "telemetry" tablespace present, telemetry_event and its indexes
#     land in it; without one, they stay in the default tablespace
# Usage: tools/database/migration-equivalence.sh   (OLD_REF=<commit> to change the baseline)
# acd24bac is the last commit with the 24-file chain (V1-V6, V8-V25) that the
# launch set (Nov 1 2026) replaced. Later migrations (V5 and up) are left out, so
# this stays a guard against anyone editing V1-V4.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
OLD_REF=${OLD_REF:-acd24bac}
MIG=backend/src/main/resources/db/migration
IMAGE=${IMAGE:-postgres:18}
name="mig-eq-$$"
work=$(mktemp -d)
trap 'docker rm -f "$name" >/dev/null 2>&1 || true; rm -rf "$work"' EXIT

docker run -d --rm --name "$name" -e POSTGRES_USER=eq -e POSTGRES_PASSWORD=eq \
  "$IMAGE" -c shared_preload_libraries=pg_stat_statements >/dev/null
for _ in $(seq 60); do docker exec "$name" pg_isready -U eq -d postgres -q 2>/dev/null && break; sleep 1; done
sleep 1
docker exec "$name" pg_isready -U eq -d postgres -q

psql_in() { docker exec -i "$name" psql -U eq -v ON_ERROR_STOP=1 -q "$@"; }

# Flyway order: by the number after V; each file in one transaction, as Flyway runs it.
ordered() { sed -nE 's/^(.*\/)?V([0-9]+)__.*\.sql$/\2 &/p' | sort -n | cut -d' ' -f2-; }

build_old() {
  psql_in -d postgres -c "CREATE DATABASE $1"
  git ls-tree --name-only "$OLD_REF" "$MIG/" | ordered | while read -r f; do
    git show "$OLD_REF:$f" | psql_in -d "$1" --single-transaction -f - >/dev/null || { echo "old chain failed at $f" >&2; exit 1; }
  done
}
build_new() {
  psql_in -d postgres -c "CREATE DATABASE $1"
  ls "$MIG"/V[1-4]__*.sql | ordered | while read -r f; do
    psql_in -d "$1" --single-transaction -f - <"$f" >/dev/null || { echo "new set failed at $f" >&2; exit 1; }
  done
}

schema() {
  docker exec "$name" pg_dump -U eq -d "$1" --schema-only --no-owner --no-acl --no-tablespaces \
    | grep -vE '^(--|SET |SELECT pg_catalog\.set_config|\\(un)?restrict )' | sed '/^$/d' \
    | grep -v 'flyway_schema_history'
}
data() {
  psql_in -d "$1" -At <<'SQL'
SELECT 'role|' || name FROM role ORDER BY 1;
SELECT 'capability|' || code FROM capability ORDER BY 1;
SELECT 'grant|' || r.name || '|' || c.code
FROM role_capability rc JOIN role r ON r.id = rc.role_id JOIN capability c ON c.id = rc.capability_id ORDER BY 1;
SELECT 'concern|' || label || '|' || (removed_at IS NULL) FROM concern ORDER BY 1;
DO $$ DECLARE t text; n bigint; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY 1 LOOP
    EXECUTE format('SELECT count(*) FROM %I', t) INTO n;
    RAISE NOTICE 'rows|%|%', t, n;
  END LOOP; END $$;
SQL
}

build_old old
build_new new
fail=0
schema old >"$work/old.schema"; schema new >"$work/new.schema"
if diff -u "$work/old.schema" "$work/new.schema" >"$work/schema.diff"; then
  echo "ok: schema identical ($(wc -l <"$work/new.schema") normalized lines)"
else
  cat "$work/schema.diff"; echo "FAIL: schema differs" >&2; fail=1
fi
data old >"$work/old.data" 2>&1; data new >"$work/new.data" 2>&1
if diff -u "$work/old.data" "$work/new.data"; then
  echo "ok: reference data identical ($(grep -c '^grant|' "$work/new.data") grants, $(grep -c '^concern|' "$work/new.data") concerns, $(grep -c 'rows|' "$work/new.data") tables)"
else
  echo "FAIL: reference data differs" >&2; fail=1
fi

placed() {
  psql_in -d "$1" -At -c "SELECT c.relname || '|' || COALESCE(t.spcname, 'pg_default')
    FROM pg_class c LEFT JOIN pg_tablespace t ON t.oid = c.reltablespace
    WHERE c.relname IN ('telemetry_event', 'telemetry_event_pkey', 'telemetry_event_user_at', 'telemetry_event_received_at')
    ORDER BY 1"
}
if placed new | grep -qv '|pg_default$'; then echo "FAIL: telemetry left the default tablespace with no telemetry tablespace" >&2; fail=1; fi
docker exec -u postgres "$name" mkdir -p /var/lib/postgresql/ts
psql_in -d postgres -c "CREATE TABLESPACE telemetry LOCATION '/var/lib/postgresql/ts'"
build_new with_ts
if [[ "$(placed with_ts | grep -c '|telemetry$')" == 4 ]]; then
  echo "ok: telemetry_event and its 3 indexes go to the telemetry tablespace when it exists"
else
  placed with_ts; echo "FAIL: telemetry not placed in the telemetry tablespace" >&2; fail=1
fi
exit "$fail"
