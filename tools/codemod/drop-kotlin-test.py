#!/usr/bin/env python3
r"""Delete named test functions from a Kotlin test file, by lines.

Usage: python3 -I tools/codemod/drop-kotlin-test.py FILE "test name" ["test name" ...]

A test is the `fun \`name\`()` line, the `@Test` and comment lines right above it, and every line
down to the brace that closes it. Braces inside strings and comments are skipped. Prints what it
cut; exits 1 and changes nothing when a name is missing. Then run `./gradlew :backend:ktlintFormat`
and drop imports or fixtures the file no longer uses.
"""

import re
import sys
from pathlib import Path


def depth_change(line: str) -> int:
    code = re.sub(r'"(?:\\.|[^"\\])*"', '""', line.split("//", 1)[0])
    return code.count("{") - code.count("}")


def cut(lines: list[str], name: str) -> tuple[int, int]:
    head = next((i for i, l in enumerate(lines) if f"fun `{name}`(" in l), None)
    if head is None:
        raise KeyError(name)
    start = head
    while start > 0 and re.match(r"\s*(@\w+|//|/\*|\*)", lines[start - 1]):
        start -= 1
    depth, end, opened = 0, head, False
    for end in range(head, len(lines)):
        depth += depth_change(lines[end])
        opened = opened or "{" in lines[end] or lines[end].rstrip().endswith("=")
        if opened and depth == 0 and not lines[end].rstrip().endswith(("=", "(", ",")):
            break
    if end + 1 < len(lines) and not lines[end + 1].strip():
        end += 1
    return start, end


def main(argv: list[str]) -> int:
    path, names = Path(argv[0]), argv[1:]
    lines = path.read_text().splitlines(keepends=True)
    for name in names:
        try:
            start, end = cut(lines, name)
        except KeyError:
            print(f"missing: {name}")
            return 1
        print(f"cut {name}: lines {start + 1}-{end + 1}")
        del lines[start : end + 1]
    path.write_text("".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
