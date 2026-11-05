#!/usr/bin/env bash
# Before/after on one box, taken back to back: two apps up at once (each on its own database copy), benched in
# alternating rounds so a change in box load lands on both sides. Reads only, so the two copies stay identical.
#   ab.sh <root-before> <db-suffix-before> <root-after> <db-suffix-after> [rounds] [n] [out.jsonl]
#   e.g. ab.sh "$(rift-ws new lt-before)" 1000b ~/Projects/company_app 1000 4 15
# Start the throwaway Postgres first (run.sh pg-up). Then: python3 stats.py <out.jsonl> <summary.csv>
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
A_ROOT=${1:?root before}; A_DB=${2:?db before}; B_ROOT=${3:?root after}; B_DB=${4:?db after}
ROUNDS=${5:-4}; N=${6:-15}; OUT=${7:-$HERE/results/ab.jsonl}
scale=${B_DB%%[!0-9]*}
LT_RUN=/tmp/lt-run-a LT_APPPORT=18401 LT_APP_ROOT=$A_ROOT "$HERE/run.sh" app "$A_DB"
LT_RUN=/tmp/lt-run-b LT_APPPORT=18402 LT_APP_ROOT=$B_ROOT "$HERE/run.sh" app "$B_DB"
for r in $(seq "$ROUNDS"); do
  # Swap the order each round so neither side always runs first.
  if (( r % 2 )); then order="18401:before 18402:after"; else order="18402:after 18401:before"; fi
  for side in $order; do
    echo "round $r ${side#*:} load $(cut -d' ' -f1 /proc/loadavg)" >&2
    node "$HERE/bench.mjs" --base "http://127.0.0.1:${side%%:*}" --scale "$scale" --label "${side#*:}" --round "$r" \
      --n "$N" --writes 0 ${LT_ONLY:+--only "$LT_ONLY"} --out "$OUT"
  done
done
echo "done: $OUT" >&2
