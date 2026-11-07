#!/usr/bin/env python3
"""Print each failed backend test with the first lines of its message.

Usage: python3 -I tools/quality/test-failures.py [results-dir] [--lines N]
Reads the JUnit XML Gradle writes (backend/build/test-results/test by default) and ends with
"N failed of M", so a long run is read in one short screen instead of scrolling Gradle's log.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main(argv: list[str]) -> int:
    lines = 3
    if "--lines" in argv:
        at = argv.index("--lines")
        lines = int(argv[at + 1])
        argv = argv[:at] + argv[at + 2 :]
    root = Path(argv[0] if argv else "backend/build/test-results/test")
    files = sorted(root.glob("TEST-*.xml"))
    if not files:
        print(f"no TEST-*.xml under {root}")
        return 2
    total = failed = 0
    for path in files:
        suite = ET.parse(path).getroot()
        for case in suite.iter("testcase"):
            total += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is None:
                continue
            failed += 1
            owner = case.get("classname", "").rsplit(".", 1)[-1]
            print(f"FAIL {owner} > {case.get('name')}")
            text = (problem.get("message") or problem.text or "").strip().splitlines()
            for line in text[:lines]:
                print(f"     {line[:220]}")
    print(f"{failed} failed of {total}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
