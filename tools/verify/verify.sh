#!/usr/bin/env bash
# One-step local stack for verification: own Postgres database, own backend port,
# own build of the web app. Never touches another instance (e.g. the dev server on :18080).
#   verify.sh up       build web, create DB, start backend, wait until usable
#   verify.sh doctor   read-only: is OUR instance up, right commit, port ours, login works
#   verify.sh shot <route> [name]   drive a page signed in, save desktop + 375px phone screenshots
#   verify.sh down     stop what `up` started, drop its DB, keep the evidence
#   verify.sh all <route>...        up, doctor, shot each route, down (down also runs on failure)
set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
HERE="$ROOT/tools/verify"
PORT=${VERIFY_PORT:-18181}
RUN=${VERIFY_RUN:-/tmp/verify-run-$PORT}
OUT=${VERIFY_OUT:-/tmp/verify-evidence}
DB=verify_$PORT
USER_NAME=${VERIFY_USER:-owner}
USER_PASS=${VERIFY_PASS:-owner-dev-pass}
NODE_BIN=${VERIFY_NODE:-}
BASE="http://127.0.0.1:$PORT"

# Node 24+ is needed (vite 8). Prefer PATH, then ~/.local/node/bin.
pick_node() {
  for n in ${NODE_BIN:+"$NODE_BIN"} "$(command -v node)" "$HOME/.local/node/bin/node"; do
    [ -x "$n" ] || continue
    [ "$("$n" -p 'process.versions.node.split(".")[0]')" -ge 24 ] && { NODE_BIN=$n; return 0; }
  done
  echo "need Node 24+ (set VERIFY_NODE)" >&2; return 1
}

# Postgres: reuse the dev server's credentials from the main checkout's .env (never printed).
load_db_env() {
  local f
  for f in "$ROOT/.env" "$(dirname "$(git -C "$ROOT" rev-parse --git-common-dir)")/.env"; do
    [ -f "$f" ] && { set -a; . "$f"; set +a; break; }
  done
  : "${DB_HOST:?no .env with DB_HOST found}" "${DB_PORT:?}" "${POSTGRES_USER:?}" "${POSTGRES_PASSWORD:?}"
  export PGPASSWORD=$POSTGRES_PASSWORD
}
psqlc() { psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -Atqc "$1"; }

up() {
  pick_node || return 1
  load_db_env
  if [ -e "$RUN/backend.pid" ] || (ss -ltn "sport = :$PORT" | grep -q LISTEN); then
    echo "port $PORT or $RUN already in use; run '$0 down' or set VERIFY_PORT" >&2; return 1
  fi
  mkdir -p "$RUN" "$OUT"
  # Web: deps once, build into our own dir (not web/dist, which another instance may serve).
  ( cd "$ROOT/web" && export PATH=$(dirname "$NODE_BIN"):$PATH
    [ -d node_modules ] || npm ci --no-audit --no-fund >"$RUN/npm.log" 2>&1
    npx vite build --outDir "$RUN/dist" --emptyOutDir >"$RUN/web-build.log" 2>&1 ) || { tail -20 "$RUN/web-build.log"; return 1; }
  psqlc "DROP DATABASE IF EXISTS $DB" && psqlc "CREATE DATABASE $DB" || return 1
  echo "$DB" >"$RUN/db"
  # Backend: Flyway migrates the empty DB, DevMain seeds owner/staff/relief logins and fixture branch.
  # Env vars win over .env, so the dev instance's settings are not used.
  cd "$ROOT" || return 1
  APP_PORT=$PORT WEB_DIR="$RUN/dist" POSTGRES_DB=$DB \
    TEST_USERNAME=owner TEST_PASSWORD=owner-dev-pass SCOPED_USERNAME=staff SCOPED_PASSWORD=staff-dev-pass \
    RELIEF_USERNAME=relief RELIEF_PASSWORD=relief-dev-pass \
    setsid nohup ./gradlew :backend:run -q >"$RUN/backend.log" 2>&1 &
  echo $! >"$RUN/backend.pid"
  # Ready = health answers AND the seeded login works (health alone can pass before the seeder ran).
  for _ in $(seq 1 120); do
    login_ok && { echo "up: $BASE (db $DB, commit $(git -C "$ROOT" rev-parse --short HEAD))"; return 0; }
    sleep 2
  done
  echo "backend not ready after 240s; tail of log:" >&2; tail -30 "$RUN/backend.log" >&2; return 1
}

login_ok() {
  curl -sf -m 3 "$BASE/health" >/dev/null || return 1
  local c; c=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -H 'content-type: application/json' \
    -d "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}" "$BASE/auth/login")
  [ "$c" = 200 ]
}

doctor() {
  local bad=0
  [ -f "$RUN/backend.pid" ] && echo "pid file: $(cat "$RUN/backend.pid")" || { echo "FAIL no pid file in $RUN (not started by us)"; bad=1; }
  local owner; owner=$(ss -ltnp "sport = :$PORT" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1)
  [ -n "$owner" ] && echo "port $PORT owned by $owner" || { echo "FAIL nothing listens on $PORT"; bad=1; }
  curl -sf -m 3 "$BASE/health" && echo || { echo "FAIL /health"; bad=1; }
  login_ok && echo "login as $USER_NAME: ok" || { echo "FAIL login as $USER_NAME"; bad=1; }
  echo "worktree commit: $(git -C "$ROOT" rev-parse --short HEAD) (dirty files: $(git -C "$ROOT" status --porcelain | wc -l))"
  [ -f "$RUN/dist/index.html" ] && echo "web build: $RUN/dist ($(stat -c %y "$RUN/dist/index.html" | cut -c1-19))" || { echo "FAIL no web build"; bad=1; }
  return $bad
}

shot() {
  pick_node || return 1
  mkdir -p "$OUT"
  "$NODE_BIN" "$HERE/drive.mjs" "$BASE" "${1:?route like #/today}" "$OUT" "${2:-}" "$USER_NAME" "$USER_PASS"
}

down() {
  load_db_env 2>/dev/null
  if [ -f "$RUN/backend.pid" ]; then
    local p; p=$(cat "$RUN/backend.pid")
    # We started it with setsid, so its pid is the process-group leader: kill the group (gradle + its java child), nothing else.
    kill -TERM -- "-$p" 2>/dev/null; sleep 2; kill -KILL -- "-$p" 2>/dev/null
  fi
  [ -f "$RUN/db" ] && psqlc "DROP DATABASE IF EXISTS $(cat "$RUN/db") WITH (FORCE)" 2>/dev/null
  rm -rf "$RUN"
  ss -ltn "sport = :$PORT" | grep -q LISTEN && { echo "WARN port $PORT still listening" >&2; return 1; }
  echo "down; evidence kept in $OUT"
}

case "${1:-}" in
  up) up ;;
  doctor) doctor ;;
  shot) shift; shot "$@" ;;
  down) down ;;
  all) shift; rc=0; up && doctor || rc=1
       if [ $rc = 0 ]; then for r in "$@"; do shot "$r" || rc=1; done; fi
       down; exit $rc ;;
  *) sed -n 2,8p "$0"; exit 2 ;;
esac
