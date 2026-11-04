#!/usr/bin/env python3
"""Comments name no issue, owed-item or chat-message ids: the reason goes in the comment, the history in git.

Usage: comment-refs.py [--fix] [PATH...]   (defaults to backend/src/main web/src)
Without --fix: prints each hit and exits 1. With --fix: strips the common id forms, then prints what is left
for a hand edit. A `SAFETY:` comment and the comment on or just above an `@Suppress` keep their #ticket:
detekt RequireSafetyComment and SuppressRequiresTicket demand one there.
"""
import pathlib, re, sys

REF = re.compile(r"#\d{2,4}\b|\bmessage \d{4,5}\b|\b[Oo]wed #\d+")
SKIP = {"web/src/api/schema.d.ts", "web/src/lib/access.gen.ts"}
COMMENT_LINE = re.compile(r"^\s*(//|\*|/\*\*?|<!--)")
TRAILING = re.compile(r"[;)}\w\"]\s+//\s")
STRIP = [
    (re.compile(r"\s*\((?:the )?user,? message \d{4,5}(?:[,:;][^)]*)?\)"), ""),
    (re.compile(r"\s*\((?:audit [A-Z]\d+[;,] )?(?:the )?user,? message \d{4,5}[^)]*\)"), ""),
    (re.compile(r"\s*\(owed #\d+[a-z()]*\)"), ""),
    (re.compile(r"\b[Oo]wed #\d+(?:\([a-z]\))?:\s*"), ""),
    (re.compile(r"\s*\(#\d{2,4}\)"), ""),
    (re.compile(r"(//\s*)#\d{2,4}:?\s+"), r"\1"),
]


def exempt(lines, i):
    if "SAFETY:" in lines[i] or "@Suppress" in lines[i]:
        return True
    return i + 1 < len(lines) and "@Suppress" in lines[i + 1]


def is_comment(line):
    return bool(COMMENT_LINE.match(line)) or (bool(TRAILING.search(line)) and "://" not in line)


def scan(path, fix):
    lines = path.read_text().split("\n")
    hits, changed = [], False
    for i, line in enumerate(lines):
        if not is_comment(line) or exempt(lines, i) or not REF.search(line):
            continue
        if fix:
            new = line
            for pat, rep in STRIP:
                new = pat.sub(rep, new)
            if new != line:
                lines[i], changed = new, True
        if REF.search(lines[i]):
            hits.append(f"{path}:{i + 1}: {lines[i].strip()[:140]}")
    if changed:
        path.write_text("\n".join(lines))
    return hits


def main(argv):
    fix = "--fix" in argv
    roots = [a for a in argv if not a.startswith("--")] or ["backend/src/main", "web/src"]
    hits = []
    for root in roots:
        base = pathlib.Path(root)
        for p in sorted(base.rglob("*") if base.is_dir() else [base]):
            if p.suffix in {".kt", ".ts", ".svelte"} and str(p) not in SKIP:
                hits += scan(p, fix)
    for h in hits:
        print(h)
    if hits:
        print(f"{len(hits)} comment lines name an issue, owed item or message id: say the reason, drop the id"
              " (tools/quality/comment-refs.py --fix strips the common forms)", file=sys.stderr)
    return 1 if hits and not fix else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
