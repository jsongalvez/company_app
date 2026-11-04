#!/usr/bin/env python3
"""Inventory comments in backend/src/main and web/src. Usage: comments.py [--kind doc|line|block|trailing|code|suppress] [--constraints]"""
import re, sys, pathlib, collections

ROOTS = ["backend/src/main", "web/src"]
SKIP = {"web/src/api/schema.d.ts", "web/src/lib/access.gen.ts"}
CONSTRAINT = re.compile(r"\b(must|never|always|keep in sync|do not|don't|IMPORTANT|only place|single (source|owner))\b", re.I)
CODEISH = re.compile(r"^\s*//\s*(val |var |fun |if \(|return |import |const |let |await |\}|[a-zA-Z_.]+\(.*\);?$)")
SUPPRESS = re.compile(r"@Suppress|eslint-disable|@ts-ignore|@ts-expect-error|svelte-ignore")


def blocks(path):
    lines = path.read_text().splitlines()
    i = 0
    while i < len(lines):
        s = lines[i].strip()
        if s.startswith("/*") or s.startswith("<!--"):
            end = "*/" if s.startswith("/*") else "-->"
            j = i
            while end not in lines[j] and j < len(lines) - 1:
                j += 1
            kind = "doc" if s.startswith("/**") else "block"
            yield kind, i + 1, lines[i : j + 1], lines[j + 1] if j + 1 < len(lines) else ""
            i = j + 1
            continue
        if s.startswith("//"):
            j = i
            while j + 1 < len(lines) and lines[j + 1].strip().startswith("//"):
                j += 1
            chunk = lines[i : j + 1]
            kind = "code" if any(CODEISH.match(c) for c in chunk) else "line"
            if all(SUPPRESS.search(c) or "prettier-ignore" in c for c in chunk):
                kind = "suppress"
            yield kind, i + 1, chunk, lines[j + 1] if j + 1 < len(lines) else ""
            i = j + 1
            continue
        if "//" in lines[i] and re.search(r"[;)}\w]\s+//\s", lines[i]) and "://" not in lines[i]:
            yield "trailing", i + 1, [lines[i]], ""
        i += 1


def files():
    for root in ROOTS:
        for p in sorted(pathlib.Path(root).rglob("*")):
            if p.suffix in {".kt", ".ts", ".svelte"} and str(p) not in SKIP:
                yield p


def main(argv):
    want = argv[argv.index("--kind") + 1] if "--kind" in argv else None
    totals = collections.Counter()
    for p in files():
        for kind, line, chunk, nxt in blocks(p):
            totals[kind] += len(chunk)
            if "--constraints" in argv and not any(CONSTRAINT.search(c) for c in chunk):
                continue
            if want == kind or ("--constraints" in argv and want is None):
                print(f"{p}:{line}: [{kind}] " + " | ".join(c.strip() for c in chunk)[:400])
    print(dict(totals), file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1:])
