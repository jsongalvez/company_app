#!/usr/bin/env bash
# A/B two local stacks with vitals.mjs, one run each in turn, so drifting load on the box hits both alike.
#   tools/perf/ab.sh <before-url> <after-url> [pairs=5] [out-dir]
# Prints the median and min-max of each metric per load, before and after.
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
A=${1:?before url}; B=${2:?after url}; N=${3:-5}; OUT=${4:-$(mktemp -d)}
mkdir -p "$OUT"
for i in $(seq "$N"); do
  for side in before after; do
    url=$A; [ "$side" = after ] && url=$B
    node "$HERE/vitals.mjs" --base "$url" --runs 1 --json "$OUT/$side-$i.json" >/dev/null 2>"$OUT/$side-$i.err" || echo "run $side $i failed: $(head -1 "$OUT/$side-$i.err")" >&2
  done
done
python3 -I - "$OUT" <<'PY'
import glob, json, statistics, sys
out = sys.argv[1]
def runs(side):
    rs = []
    for f in sorted(glob.glob(f"{out}/{side}-*.json")):
        rs += json.load(open(f))
    return rs
b, a = runs("before"), runs("after")
print(f"runs: before {len(b)}, after {len(a)}  ({out})")
cols = ["ttfb", "fcp", "lcp", "usable", "tbt", "cls", "inp", "jsKB"]
for load in ["cold login", "cold Today", "warm Today", "no-SW Today"]:
    print(load)
    for c in cols:
        cell = []
        for rs in (b, a):
            xs = sorted(r[load][c] for r in rs if load in r and isinstance(r[load].get(c), (int, float)) and r[load][c] == r[load][c])
            cell.append(f"{statistics.median(xs):8.1f} ({xs[0]:.0f}-{xs[-1]:.0f})" if xs else "       -")
        print(f"  {c:7} before {cell[0]:24} after {cell[1]}")
PY
