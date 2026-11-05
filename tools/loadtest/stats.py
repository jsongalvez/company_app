#!/usr/bin/env python3
"""Median and 95% bootstrap confidence interval per (label, scale, route) from bench.mjs's JSON lines.

    python3 stats.py results/raw.jsonl results/summary.csv

The measure is server time (Server-Timing db + app, whole ms); `wall` is the client's time and is kept as a check.
Two medians differ for real when their intervals do not overlap.
"""
import csv
import json
import random
import statistics
import sys
from collections import defaultdict


def ci(xs, reps=2000, seed=7):
    rnd = random.Random(seed)
    meds = sorted(statistics.median(rnd.choices(xs, k=len(xs))) for _ in range(reps))
    return meds[int(0.025 * reps)], meds[int(0.975 * reps) - 1]


def main(src, dst):
    groups = defaultdict(lambda: {"server": [], "db": [], "wall": []})
    for line in open(src):
        r = json.loads(line)
        g = groups[(r["label"], int(r["scale"]), r["route"])]
        if r["db"] is not None and r["app"] is not None:
            g["server"].append(r["db"] + r["app"])
            g["db"].append(r["db"])
        g["wall"].append(r["wall"])
    rows = []
    for (label, scale, route), g in sorted(groups.items(), key=lambda kv: (kv[0][2], kv[0][1], kv[0][0])):
        xs = g["server"] or g["wall"]
        lo, hi = ci(xs)
        rows.append({
            "route": route, "scale": scale, "label": label, "n": len(xs),
            "measure": "server_ms" if g["server"] else "wall_ms",
            "median_ms": statistics.median(xs), "ci_low": lo, "ci_high": hi,
            "db_median_ms": statistics.median(g["db"]) if g["db"] else "",
            "wall_median_ms": round(statistics.median(g["wall"]), 1),
        })
    with open(dst, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    for r in rows:
        print(f"{r['route']:<28} x{r['scale']:<5} {r['label']:<7} n={r['n']:<3} "
              f"{r['median_ms']:>7} [{r['ci_low']}, {r['ci_high']}] db {r['db_median_ms']}  wall {r['wall_median_ms']}")


if __name__ == "__main__":
    main(*sys.argv[1:3])
