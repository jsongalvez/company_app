#!/usr/bin/env python3
"""Time one query under two variants in alternating rounds, from EXPLAIN ANALYZE's Execution Time.

    python3 sql-ab.py --query q.sql --a lt_x1000 --b lt_x1000 --b-pre "DROP INDEX idx_client_name_live;" [--rounds 15]

A variant is a database plus SQL run first inside the same transaction, which is rolled back, so
`--b-pre "DROP INDEX x;"` times the plan without index x and leaves the database as it was. The query file holds
one statement with no trailing EXPLAIN; `SET` lines before it are allowed. Prints median and 95% bootstrap
interval per variant (stats.py's ci), plus planning time. Runs against the load-test Postgres only.
"""
import argparse
import os
import re
import statistics
import subprocess
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from stats import ci  # noqa: E402


def run(db, pre, query):
    sql = f"BEGIN;\n{pre}\nEXPLAIN (ANALYZE, BUFFERS) {query}\nROLLBACK;\n"
    out = subprocess.run(
        ["psql", "-h", "127.0.0.1", "-p", os.environ.get("LT_PGPORT", "55433"), "-U", "lt", "-d", db,
         "-v", "ON_ERROR_STOP=1", "-qAt"],
        input=sql, capture_output=True, text=True, env={**os.environ, "PGPASSWORD": "lt"}, check=True,
    ).stdout
    ex = float(re.search(r"Execution Time: ([\d.]+)", out).group(1))
    pl = float(re.search(r"Planning Time: ([\d.]+)", out).group(1))
    return ex, pl


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--query", required=True)
    p.add_argument("--a", required=True)
    p.add_argument("--b", required=True)
    p.add_argument("--a-pre", default="")
    p.add_argument("--b-pre", default="")
    p.add_argument("--rounds", type=int, default=15)
    p.add_argument("--warm", type=int, default=2)
    args = p.parse_args()
    text = open(args.query).read().strip().rstrip(";")
    sets = "\n".join(l for l in text.splitlines() if l.upper().startswith("SET "))
    query = "\n".join(l for l in text.splitlines() if not l.upper().startswith("SET ")) + ";"
    variants = {"a": (args.a, sets + "\n" + args.a_pre), "b": (args.b, sets + "\n" + args.b_pre)}
    res = {k: [] for k in variants}
    for i in range(args.warm + args.rounds):
        for k, (db, pre) in variants.items():
            r = run(db, pre, query)
            if i >= args.warm:
                res[k].append(r)
    for k, (db, pre) in variants.items():
        ex = [r[0] for r in res[k]]
        pl = [r[1] for r in res[k]]
        lo, hi = ci(ex)
        label = f"{db} {args.a_pre if k == 'a' else args.b_pre}".strip()
        print(f"{k}: exec {statistics.median(ex):.1f} [{lo:.1f}, {hi:.1f}] ms  plan {statistics.median(pl):.1f} ms  n={len(ex)}  ({label})")


if __name__ == "__main__":
    main()
