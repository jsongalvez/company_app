#!/usr/bin/env python3
"""Which statements does each request run, and which does it repeat?

Start the backend with SLOW_STATEMENT_MS=0 so it logs every statement, drive the
app, then:

    python3 tools/perf/sql-trace.py /tmp/verify-run-18181/backend.log [--route clock-in] [--top 15]

Per route (slowest request kept): the statement count, then each distinct SQL with
how often it ran. A statement run once per row (N+1) shows as `12x`.
"""
import argparse
import collections
import json
import re
import sys

ANSI = re.compile(r"\x1b\[[0-9;]*m")


def events(path):
    with open(path, errors="replace") as f:
        for line in f:
            i = line.find('{"event"')
            if i < 0:
                continue
            try:
                yield json.loads(ANSI.sub("", line[i:]).strip())
            except json.JSONDecodeError:
                continue


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("log")
    ap.add_argument("--route", default="", help="substring of the route template")
    ap.add_argument("--top", type=int, default=12, help="routes to show, most queries first")
    a = ap.parse_args()

    sql = collections.defaultdict(list)
    done = {}
    for e in events(a.log):
        if e.get("event") == "slow_statement":
            sql[e["traceId"]].append(re.sub(r"\s+", " ", e.get("sql", "")))
        elif e.get("event") == "request_completed":
            done[e["traceId"]] = e
    if not sql:
        sys.exit("no slow_statement lines: start the backend with SLOW_STATEMENT_MS=0")

    worst = {}
    for t, e in done.items():
        key = f'{e["method"]} {e.get("template", e["route"])}'
        if a.route not in key or t not in sql:
            continue
        if key not in worst or len(sql[t]) > len(sql[worst[key]]):
            worst[key] = t
    for key, t in sorted(worst.items(), key=lambda kv: -len(sql[kv[1]]))[: a.top]:
        stmts = sql[t]
        repeats = sum(n - 1 for n in collections.Counter(stmts).values())
        print(f"\n{key}: {len(stmts)} statements, {repeats} repeats (trace {t})")
        for s, n in collections.Counter(stmts).most_common():
            print(f"  {n:3d}x {s[:220]}")


if __name__ == "__main__":
    main()
