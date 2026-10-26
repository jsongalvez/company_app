#!/usr/bin/env bash
# Step up the VU count until two levels in a row fail their limits. Usage: ladder.sh <prefix> <think_min> <think_max> <hold_s> <vus>...
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PREFIX=$1; TMIN=$2; TMAX=$3; HOLD=$4; shift 4
bad=0
for v in "$@"; do
  "$HERE/run.sh" "$PREFIX-$v" "$v" "$TMIN" "$TMAX" "$HOLD" 2>&1 | tee "${OUT:-/tmp/capacity-out}/$PREFIX-$v.report"
  if grep -q "k6 rc=0" "${OUT:-/tmp/capacity-out}/$PREFIX-$v.report"; then bad=0; else bad=$((bad + 1)); echo "LEVEL $v FAILED limits ($bad in a row)"; [ "$bad" -ge 2 ] && break; fi
done
