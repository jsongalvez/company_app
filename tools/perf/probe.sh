#!/usr/bin/env bash
# Timer entry: one probe run against the live app, then a pg_stat_statements snapshot for perf-slow.
# Settings in ~/.config/company-app-probe.env: PROBE_USER, PROBE_PASS, PROBE_DB_CONTAINER (and PROBE_BASE to override the app address).
set -uo pipefail
. "$HOME/.config/company-app-probe.env"
. "$HOME/.config/company-app-deploy.env"   # COOLIFY_APP_UUID
HERE=$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)
SNAP_DIR=${SNAP_DIR:-$HOME/.local/state/company-app-perf/pgss}; mkdir -p "$SNAP_DIR"

if [[ -z "${PROBE_BASE:-}" ]]; then
  c=$(docker ps -q --filter "label=coolify.applicationUuid=$COOLIFY_APP_UUID" | head -1)
  [[ -n "$c" ]] || { echo "app container not found" >&2; exit 1; }
  PROBE_BASE="http://$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$c" | awk '{print $1}'):3023"
fi
export PROBE_BASE PROBE_USER PROBE_PASS

snapshot() { # queryid, total ms, calls, query, one line each
  local out
  out="$SNAP_DIR/$(date +%s).tsv"
  if [[ -n "${PGSS_CMD:-}" ]]; then $PGSS_CMD >"$out"; else
    docker exec "$PROBE_DB_CONTAINER" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atq -F "	" -c "select queryid, total_exec_time, calls, regexp_replace(query, E\"[\\n\\t]+\", \" \", \"g\") from pg_stat_statements where dbid = (select oid from pg_database where datname = current_database()) order by total_exec_time desc limit 80"' >"$out" 2>/dev/null
  fi || true
  [[ -s "$out" ]] || rm -f "$out"
  for old in $(ls -1t "$SNAP_DIR"/*.tsv 2>/dev/null | tail -n +400); do rm -f "$old"; done
}

snapshot
node "$HERE/probe.mjs"; rc=$?
snapshot
exit $rc
