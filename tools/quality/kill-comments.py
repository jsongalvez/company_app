#!/usr/bin/env python3
"""Delete comment runs that carry history instead of a reason the code can't show.

Usage: kill-comments.py [--dry] PATH...   (defaults to backend/src/main web/src)
A // run or a /** */ block of up to MAX_DOC lines dies whole when any line matches KILL.
Longer doc blocks that match are printed for a hand edit. The same patterns are refused
by detekt ForbiddenComment (backend) and web/tests/unit/comments.test.ts (web).
"""
import pathlib, re, sys

KILL = re.compile(
    r"\b[0-9a-f]{8}\b"          # commit or feedback id: history lives in git
    r"|\bprecedent\b"           # "same as X": point at code, not at a story
    r"|stays whole"             # sermons defending a @Suppress
)
MAX_DOC = 4
SKIP = {"web/src/api/schema.d.ts", "web/src/lib/access.gen.ts"}


def edit(path, dry):
    lines = path.read_text().split("\n")
    out, long_docs, killed, i = [], [], 0, 0
    while i < len(lines):
        s = lines[i].strip()
        if s.startswith("//"):
            j = i
            while j + 1 < len(lines) and lines[j + 1].strip().startswith("//") and "@Suppress" not in lines[j + 1]:
                j += 1
            run = lines[i : j + 1]
            if any(KILL.search(l) for l in run) and not any("prettier-ignore" in l or "svelte-ignore" in l for l in run):
                killed += len(run)
            else:
                out += run
            i = j + 1
            continue
        if s.startswith("/**"):
            j = i
            while "*/" not in lines[j]:
                j += 1
            run = lines[i : j + 1]
            if any(KILL.search(l) for l in run):
                if len(run) <= MAX_DOC:
                    killed += len(run)
                    i = j + 1
                    continue
                long_docs.append(i + 1)
            out += run
            i = j + 1
            continue
        out.append(lines[i])
        i += 1
    if killed and not dry:
        path.write_text("\n".join(out))
    for n in long_docs:
        print(f"{path}:{n}: long doc block matches, edit by hand")
    return killed


def main(argv):
    dry = "--dry" in argv
    roots = [a for a in argv if not a.startswith("--")] or ["backend/src/main", "web/src"]
    total = 0
    for root in roots:
        for p in sorted(pathlib.Path(root).rglob("*")):
            if p.suffix in {".kt", ".ts", ".svelte"} and str(p) not in SKIP:
                total += edit(p, dry)
    print(f"{'would delete' if dry else 'deleted'} {total} comment lines", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1:])
