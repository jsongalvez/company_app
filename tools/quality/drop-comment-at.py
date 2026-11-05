#!/usr/bin/env python3
"""Delete whole comment blocks by position, for rule comments a named test already enforces.

Usage: drop-comment-at.py [--dry] FILE:LINE...
LINE is where the block starts, as `comments.py --constraints` prints it: a `/**` or `/*` doc block
up to its `*/`, or a run of `//` lines. Refuses (exit 2, nothing written) when LINE is not a comment start.
"""
import collections, pathlib, sys


def block_end(lines, i):
    s = lines[i].strip()
    if s.startswith("/*"):
        j = i
        while "*/" not in lines[j]:
            j += 1
        return j
    if s.startswith("//"):
        j = i
        while j + 1 < len(lines) and lines[j + 1].strip().startswith("//"):
            j += 1
        return j
    return None


def main(argv):
    dry = "--dry" in argv
    targets = collections.defaultdict(list)
    for a in (x for x in argv if not x.startswith("--")):
        path, line = a.rsplit(":", 1)
        targets[path].append(int(line) - 1)
    edits = {}
    for path, starts in targets.items():
        lines = pathlib.Path(path).read_text().split("\n")
        drop = set()
        for i in starts:
            end = block_end(lines, i) if i < len(lines) else None
            if end is None:
                print(f"{path}:{i + 1}: not a comment start: {lines[i].strip()[:80] if i < len(lines) else 'EOF'}", file=sys.stderr)
                return 2
            drop.update(range(i, end + 1))
        edits[path] = (lines, drop)
    total = 0
    for path, (lines, drop) in edits.items():
        total += len(drop)
        if not dry:
            pathlib.Path(path).write_text("\n".join(l for k, l in enumerate(lines) if k not in drop))
    print(f"{'would delete' if dry else 'deleted'} {total} comment lines in {len(edits)} files", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
