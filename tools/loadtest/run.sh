#!/usr/bin/env bash
# Database load test on a throwaway Postgres (never prod). See README.md.
#   run.sh pg-up | fresh | seed <scale> | app <scale> | bench <scale> <label> [n] | sweep <label> [n] | stats | down
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
HERE="$ROOT/tools/loadtest"
PGPORT=${LT_PGPORT:-55433}
APPPORT=${LT_APPPORT:-18401}
RUN=${LT_RUN:-/tmp/lt-run}
DATA=${LT_DATA:-/mnt/data/loadtest/pg}
OUT=${LT_OUT:-$HERE/results}
export PGPASSWORD=lt
psqlx() { psql -h 127.0.0.1 -p "$PGPORT" -U lt -v ON_ERROR_STOP=1 -q "$@"; }
mkdir -p "$RUN/web/assets" "$OUT"
[ -f "$RUN/web/index.html" ] || echo '<html></html>' >"$RUN/web/index.html"

app_stop() {
  [ -f "$RUN/pid" ] && { kill -- -"$(cat "$RUN/pid")" 2>/dev/null || kill "$(cat "$RUN/pid")" 2>/dev/null || true; rm -f "$RUN/pid"; }
  for _ in $(seq 30); do ss -ltn "sport = :$APPPORT" | grep -q LISTEN || return 0; sleep 1; done
  echo "port $APPPORT still busy" >&2; return 1
}
app_start() { # $1 = database
  app_stop
  cd "${LT_APP_ROOT:-$ROOT}" # LT_APP_ROOT: a worktree of the commit to measure, for a clean "before"
  # JWT and dummy-password settings from the main checkout's .env (a worktree has none); the DB settings below win.
  [ -f "$ROOT/.env" ] && { set -a; . "$ROOT/.env"; set +a; }
  DB_HOST=127.0.0.1 DB_PORT=$PGPORT POSTGRES_USER=lt POSTGRES_PASSWORD=lt POSTGRES_DB=$1 APP_PORT=$APPPORT \
    WEB_DIR=$RUN/web API_DOCS= TEST_USERNAME=owner TEST_PASSWORD=owner-dev-pass SCOPED_USERNAME=staff \
    SCOPED_PASSWORD=staff-dev-pass RELIEF_USERNAME=relief RELIEF_PASSWORD=relief-dev-pass \
    setsid nohup ./gradlew :backend:run -q >"$RUN/backend-$1.log" 2>&1 &
  echo $! >"$RUN/pid"
  for _ in $(seq 120); do curl -sf -m 2 "http://127.0.0.1:$APPPORT/health" >/dev/null && { echo "app up on $APPPORT ($1)"; return 0; }; sleep 2; done
  tail -20 "$RUN/backend-$1.log" >&2; return 1
}

case "${1:-}" in
  pg-up)
    docker ps -a --format '{{.Names}}' | grep -qx company-loadtest-pg && { docker start company-loadtest-pg >/dev/null; } || \
    docker run -d --name company-loadtest-pg -p 127.0.0.1:$PGPORT:5432 -e POSTGRES_USER=lt -e POSTGRES_PASSWORD=lt \
      -v "$DATA":/var/lib/postgresql postgres:18 -c shared_preload_libraries=pg_stat_statements,auto_explain \
      -c shared_buffers=1GB -c work_mem=16MB -c max_wal_size=4GB -c synchronous_commit=off >/dev/null
    for _ in $(seq 30); do psqlx -d postgres -c 'select 1' >/dev/null 2>&1 && { echo "pg up on $PGPORT"; exit 0; }; sleep 1; done; exit 1 ;;
  fresh) # migrated schema + DevMain logins, used as the template for every scale
    psqlx -d postgres -c "DROP DATABASE IF EXISTS lt_fresh WITH (FORCE)" -c "CREATE DATABASE lt_fresh"
    app_start lt_fresh; app_stop; echo "lt_fresh ready" ;;
  seed)
    s=${2:?scale}; app_stop
    psqlx -d postgres -c "DROP DATABASE IF EXISTS lt_x$s WITH (FORCE)" -c "CREATE DATABASE lt_x$s TEMPLATE lt_fresh"
    psqlx -d "lt_x$s" -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"
    t0=$SECONDS; psqlx -d "lt_x$s" -v scale="$s" -f "$HERE/seed.sql" | tail -30; echo "seed $s: $((SECONDS - t0))s" ;;
  app) app_start "lt_x${2:?scale}" ;;
  bench) # bench <scale> <label> [n]
    s=${2:?scale}; l=${3:?label}; n=${4:-30}
    psqlx -d "lt_x$s" -c "SELECT pg_stat_statements_reset()" >/dev/null
    node "$HERE/bench.mjs" --base "http://127.0.0.1:$APPPORT" --scale "$s" --label "$l" --n "$n" --login-n "${LT_LOGIN_N:-0}" ${LT_ONLY:+--only "$LT_ONLY"} --out "$OUT/raw.jsonl"
    psqlx -d "lt_x$s" -A -F $'\t' -c "SELECT calls, round(total_exec_time::numeric,1) total_ms, round(mean_exec_time::numeric,2) mean_ms, rows, left(regexp_replace(query, '\s+', ' ', 'g'), 400) FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database()) ORDER BY total_exec_time DESC LIMIT 25" >"$OUT/statements-x$s-$l.tsv"
    echo "top statements: $OUT/statements-x$s-$l.tsv" ;;
  sweep) # sweep <label> [n]: app + bench at every scale in $LT_SCALES (default "1 10 100 1000"), then stats
    for s in ${LT_SCALES:-1 10 100 1000}; do "$0" app "$s"; "$0" bench "$s" "${2:?label}" "${3:-30}"; done
    "$0" stats >"$OUT/summary.txt"; echo "summary: $OUT/summary.txt" ;;
  stats) python3 "$HERE/stats.py" "$OUT/raw.jsonl" "$OUT/summary.csv" ;;
  down) app_stop; docker stop company-loadtest-pg >/dev/null; echo stopped ;;
  *) sed -n 2,3p "$0"; exit 2 ;;
esac
