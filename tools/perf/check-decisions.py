#!/usr/bin/env python3
"""Check a perf decision log (TSV, one row per attempt) for the gaps that made past perf claims wrong.

usage: check-log.py <decisions.tsv> [--due]
  exit 1 and one line per bad row; --due also lists kept rows still waiting for a prod measurement.
"""
import csv
import re
import sys

COLUMNS = ["id", "hypothesis", "change", "before", "after", "delta", "tests", "verdict", "note"]
VERDICTS = ("baseline", "kept", "dropped", "reverted")
GUARD = re.compile(r"fail(s|ed)?\b[^;]*?\b(before|without|on (the )?old)")


def empty(v):
    return v.strip() in ("", "-")


def check(rows):
    errors, due = [], []
    ids = [r["id"] for r in rows]
    for r in rows:
        rid, verdict = r["id"], r["verdict"].strip().lower()
        where = f"row {rid}"
        if ids.count(rid) > 1:
            errors.append(f"{where}: id used twice")
        if not verdict.startswith(VERDICTS):
            errors.append(f"{where}: verdict must start with one of {', '.join(VERDICTS)}, got {r['verdict']!r}")
            continue
        if verdict.startswith("baseline"):
            continue
        if empty(r["before"]) or empty(r["after"]):
            errors.append(f"{where}: before and after must both hold a measured number")
        if not verdict.startswith("kept"):
            continue
        measurement = r["change"].strip().lower().startswith("none")
        if not measurement:
            tests = r["tests"].strip().lower()
            if not GUARD.search(tests) and not tests.startswith("no guard:"):
                errors.append(f"{where}: kept change needs a guard that failed before the change "
                              f"(name it in tests), or 'no guard: <reason>'")
        prod_row = f"{rid}p" in ids
        prod_seen = prod_row or re.search(r"\bprod\b", r["after"] + " " + r["note"], re.I) or measurement
        if "pending prod" in verdict:
            if not prod_row:
                due.append(rid)
        elif not prod_seen:
            errors.append(f"{where}: kept without a prod measurement; add row {rid}p, "
                          f"or mark the verdict 'kept (pending prod)'")
    return errors, due


def main(argv):
    if not argv or argv[0].startswith("-"):
        print(__doc__.strip())
        return 2
    with open(argv[0], newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        if reader.fieldnames != COLUMNS:
            print(f"{argv[0]}: header must be {' '.join(COLUMNS)} (tab-separated), got {reader.fieldnames}")
            return 1
        rows = [{k: (v or "") for k, v in r.items()} for r in reader]
    errors, due = check(rows)
    for e in errors:
        print(f"{argv[0]}: {e}")
    if "--due" in argv:
        for rid in due:
            print(f"{argv[0]}: row {rid}: kept, prod measurement still due (add row {rid}p)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
