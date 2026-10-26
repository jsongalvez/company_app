#!/usr/bin/env bash
# Boots the base and the head backend on one Postgres, drives the key routes against each in
# interleaved rounds, and compares them. Usage: run.sh <base_dir> <head_dir> <out_dir>
# Needs the backend's env (DB_HOST, DB_PORT, POSTGRES_USER, POSTGRES_PASSWORD, JWT_*, ...) and k6.
# Exit: 0 pass, 1 slower or more queries, 3 a backend did not boot, 4 a k6 request failed.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
BASE_DIR=$(cd "${1:?base dir}" && pwd); HEAD_DIR=$(cd "${2:?head dir}" && pwd); OUT=${3:?out dir}
mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd)
ROUNDS=${ROUNDS:-4}
BASE_PORT=${BASE_PORT:-18191}; HEAD_PORT=${HEAD_PORT:-18192}
BOOT_WAIT_S=${BOOT_WAIT_S:-240}
export PGPASSWORD=${POSTGRES_PASSWORD:?}
declare -A DIR=([base]=$BASE_DIR [head]=$HEAD_DIR) PORT=([base]=$BASE_PORT [head]=$HEAD_PORT) DB=([base]=gate_base [head]=gate_head)
SECONDS=0
stamp() { echo "[$(printf '%3ds' "$SECONDS")] $*"; }

# shellcheck disable=SC2317  # called by the EXIT trap
stop_all() {
  for side in base head; do
    pid=$(cat "$OUT/$side.pid" 2>/dev/null) && kill -- "-$pid" 2>/dev/null
    lp=$(ss -ltnp "sport = :${PORT[$side]}" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2)
    [ -n "$lp" ] && kill "$lp" 2>/dev/null
  done
  true
}
trap stop_all EXIT

for side in base head; do
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS ${DB[$side]}" -c "CREATE DATABASE ${DB[$side]}" || exit 3
done

for side in head base; do
  stamp "build $side"
  (cd "${DIR[$side]}" && ./gradlew :backend:classes :backend:devClasses -q) >"$OUT/$side.build.log" 2>&1 \
    || { echo "$side build failed:"; tail -60 "$OUT/$side.build.log"; exit 3; }
done

for side in base head; do
  stamp "boot $side on ${PORT[$side]}"
  (cd "${DIR[$side]}" && APP_PORT=${PORT[$side]} WEB_DIR= POSTGRES_DB=${DB[$side]} setsid nohup ./gradlew :backend:run -q >"$OUT/$side.log" 2>&1 & echo $! >"$OUT/$side.pid")
done
for side in base head; do
  up=0
  for _ in $(seq 1 $((BOOT_WAIT_S / 2))); do
    curl -sf "localhost:${PORT[$side]}/health" >/dev/null && { up=1; break; }
    sleep 2
  done
  [ "$up" = 1 ] || { echo "$side backend did not boot in ${BOOT_WAIT_S}s. Boot log:"; tail -80 "$OUT/$side.log"; exit 3; }
done
stamp "both up"

run_side() { # side label: drives the key routes once, keeps this run's log lines as $OUT/<side>-<label>.log
  local side=$1 label=$2 n=$((++RUNS))
  local off; off=$(wc -c <"$OUT/$side.log")
  # Login allows 10 per minute per source address, so every run gets its own loopback address.
  k6 run --quiet --local-ips "127.0.0.$((10 + n))" -e BASE="http://127.0.0.1:${PORT[$side]}" "$HERE/key-routes.js" >"$OUT/$side-$label.k6.txt" 2>&1
  local rc=$?
  tail -c +$((off + 1)) "$OUT/$side.log" >"$OUT/$side-$label.log"
  if [ $rc -ne 0 ]; then echo "k6 failed on $side ($label):"; tail -30 "$OUT/$side-$label.k6.txt"; exit 4; fi
  stamp "$side $label done"
}
RUNS=0
run_side base warmup; run_side head warmup
rm -f "$OUT/base-warmup.log" "$OUT/head-warmup.log"
for r in $(seq 1 "$ROUNDS"); do
  if [ $((r % 2)) -eq 1 ]; then run_side base "r$r"; run_side head "r$r"; else run_side head "r$r"; run_side base "r$r"; fi
done

node "$HERE/compare.mjs" "$OUT" "$OUT/summary.md"
RC=$?
stamp "gate finished rc=$RC"
exit $RC
