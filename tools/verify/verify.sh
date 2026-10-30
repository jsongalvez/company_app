#!/usr/bin/env bash
# One-step local stack for verification: own Postgres database, own backend port,
# own build of the web app. Never touches another instance (e.g. the dev server on :18080).
#   verify.sh up       build web, create DB, start backend, wait until usable
#   verify.sh doctor   read-only: is OUR instance up, right commit, port ours, login works
#   verify.sh shot <route> [name]   drive a page signed in, save desktop + 375px phone screenshots
#   verify.sh down     stop what `up` started, drop its DB, keep the evidence
#   verify.sh all <route>...        up, doctor, shot each route, down (down also runs on failure)
#   verify.sh film <route> [button...]  film every painted frame of a reload (or of tapping each button), count blank frames
#   verify.sh at <commit> <command...>  run a command above against a copy of <commit>, with these tools (port 18282 unless VERIFY_PORT)
#   verify.sh before-after <route>...   shot each route on the merge-base with origin/master and on HEAD (needs `up`); tools/push.sh wants this for visible web changes
#   env for shots: VERIFY_SHOT_USER/VERIFY_SHOT_PASS (who), VERIFY_BRANCH (branch name to pick), VERIFY_CLOCKIN=0, VERIFY_SEED_FROM_HEAD=1 (for at/before-after)
# VERIFY_DUMP=<pg_dump -Fc file> makes `up` start from that database instead of an empty one (tools/replay).
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
  if [ -n "${VERIFY_DUMP:-}" ]; then
    # The dev database's own pg_restore: the host's may be older than the server that made the dump.
    docker exec -i -e PGPASSWORD="$PGPASSWORD" "${VERIFY_DB_CONTAINER:-company-app-devdb}" \
      pg_restore -U "$POSTGRES_USER" -d "$DB" --no-owner --no-acl --no-tablespaces <"$VERIFY_DUMP" >"$RUN/restore.log" 2>&1 \
      || { tail -5 "$RUN/restore.log"; return 1; }
  fi
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
    login_ok && { seed_test_data || return 1; echo "up: $BASE (db $DB, commit $(git -C "$ROOT" rev-parse --short HEAD))"; return 0; }
    sleep 2
  done
  echo "backend not ready after 240s; tail of log:" >&2; tail -30 "$RUN/backend.log" >&2; return 1
}

# Same data CI seeds (browser.yml), once DevMain has made the fixture branch and its logins.
seed_test_data() {
  [ -n "${VERIFY_DUMP:-}" ] && return 0
  local f
  for f in "$ROOT"/tools/seed/*.sql; do
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d "$DB" -v ON_ERROR_STOP=1 -q \
      -f "$f" >"$RUN/seed.log" 2>&1 || { echo "seed $(basename "$f") failed:" >&2; cat "$RUN/seed.log" >&2; return 1; }
  done
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
  # VERIFY_SHOT_USER/VERIFY_SHOT_PASS shoot as someone else (a seeded role user, say) than the login `up` checks.
  "$NODE_BIN" "$HERE/drive.mjs" "$BASE" "${1:?route like #/today}" "$OUT" "${2:-}" \
    "${VERIFY_SHOT_USER:-$USER_NAME}" "${VERIFY_SHOT_PASS:-$USER_PASS}"
}

film() {
  pick_node || return 1
  mkdir -p "$OUT"
  VERIFY_USER=$USER_NAME VERIFY_PASS=$USER_PASS "$NODE_BIN" "$HERE/film.mjs" "$BASE" "${1:?route like #/today}" "$OUT" "${@:2}"
}

# A copy of an older commit, so a symptom can be checked against the build that had it (before bisecting, say).
at() {
  local c; c=$(git -C "$ROOT" rev-parse --short "${1:?commit}") || return 1; shift
  local wt=/tmp/verify-at-$c rc=0
  [ -d "$wt" ] || git -C "$ROOT" worktree add -q --detach "$wt" "$c" || return 1
  mkdir -p "$wt/tools/verify" && cp -r "$HERE"/. "$wt/tools/verify/"
  # VERIFY_SEED_FROM_HEAD=1: seed the old copy with today's test data (a role user it lacks, say), so a
  # before shot can sign in as the same person as the after shot.
  [ "${VERIFY_SEED_FROM_HEAD:-}" = 1 ] && cp "$ROOT"/tools/seed/*.sql "$wt/tools/seed/"
  [ -e "$wt/web/node_modules" ] || ln -s "$ROOT/web/node_modules" "$wt/web/node_modules"
  export VERIFY_PORT=${VERIFY_PORT:-18282}
  "$wt/tools/verify/verify.sh" up || rc=1
  [ $rc = 0 ] && { "$wt/tools/verify/verify.sh" "$@" || rc=1; }
  "$wt/tools/verify/verify.sh" down >/dev/null
  git -C "$ROOT" worktree remove --force "$wt"
  return $rc
}

# Before and after shots of each route, plus the stamp tools/quality/shot-gate.sh reads.
before_after() {
  [ $# -gt 0 ] || { echo "before-after: name at least one route, like '#/today'" >&2; return 2; }
  git -C "$ROOT" diff --quiet HEAD -- web || { echo "before-after: commit the web change first; the after shot must show HEAD" >&2; return 1; }
  doctor >/dev/null || { echo "before-after: the verify copy on $PORT is not up; run verify.sh up" >&2; return 1; }
  local base head dir r slug
  base=$(git -C "$ROOT" merge-base HEAD origin/master) || return 1
  head=$(git -C "$ROOT" rev-parse HEAD)
  dir="$OUT/before-after/${head:0:8}"
  # VERIFY_ADD=1 adds to HEAD's shots (another person's view, say) instead of starting over.
  local kept=""
  if [ "${VERIFY_ADD:-}" = 1 ] && [ -f "$dir/stamp" ]; then kept=$(grep '^route=' "$dir/stamp"); else rm -rf "$dir"; fi
  mkdir -p "$dir"
  local OUT="$dir" who="${VERIFY_SHOT_USER:+$VERIFY_SHOT_USER-}"
  for r in "$@"; do
    slug=$who$(printf '%s' "$r" | tr -c 'A-Za-z0-9' '-' | sed 's/^-*//; s/-*$//')
    # The before shot may show the very problems the change fixes: only a missing shot stops us.
    VERIFY_OUT="$dir" at "$base" shot "$r" "before-$slug" || echo "before-after: the before shot of $r saw problems (above)"
    ls "$dir"/*"-before-$slug-"*.png >/dev/null 2>&1 || { echo "before-after: no before shot of $r" >&2; return 1; }
    shot "$r" "after-$slug" || return 1
  done
  { echo "base=$base"; echo "head=$head"; [ -n "$kept" ] && echo "$kept"; printf "route=${who:+$who }%s\n" "$@"
    find "$dir" -name '*.png' | sort | sed 's/^/png=/'; } > "$dir/stamp"
  echo "before-after: $(grep -c '^png=' "$dir/stamp") shots in $dir"
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
  film) shift; film "$@" ;;
  at) shift; at "$@" ;;
  before-after) shift; before_after "$@" ;;
  all) shift; rc=0; up && doctor || rc=1
       if [ $rc = 0 ]; then for r in "$@"; do shot "$r" || rc=1; done; fi
       down; exit $rc ;;
  *) sed -n 2,10p "$0"; exit 2 ;;
esac
