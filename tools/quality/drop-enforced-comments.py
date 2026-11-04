#!/usr/bin/env python3
"""Delete comment sentences that restate a rule a test already enforces.

Usage: drop-enforced-comments.py [--dry] [PATH...]   (defaults to backend/src/main)
Each RULE names the test that fails when the rule breaks, so the sentence adds nothing to the code.
A doc block left with no text is removed whole.
"""
import pathlib, re, sys

S = r"\s*\*?\s*"
RULES = [
    # SemanticOwnershipArchitectureTest: in-transaction stores open no transaction of their own
    (r"^\s*/\*\* In-transaction store operation \(ADR-0024\) — runs on the caller's command transaction\. \*/\n", ""),
    (r"^\s*\* In-transaction store operation \(ADR-0024\) — runs on the caller's command transaction\.\n", ""),
    (r"^(\s*\*) In-transaction store operation \(ADR-0024\) — runs on the caller's command\n\s*\* transaction\.(?: (.*))?\n",
     lambda m: f"{m.group(1)} {m.group(2)}\n" if m.group(2) else ""),
]
EMPTY_DOC = re.compile(r"^\s*/\*\*\n\s*\*/\n", re.M)


def edit(path, dry):
    text = path.read_text()
    new = text
    for pat, rep in RULES:
        new = re.sub(pat, rep, new, flags=re.M)
    new = EMPTY_DOC.sub("", new)
    if new != text and not dry:
        path.write_text(new)
    return text.count("\n") - new.count("\n")


def main(argv):
    dry = "--dry" in argv
    roots = [a for a in argv if not a.startswith("--")] or ["backend/src/main"]
    total = sum(edit(p, dry) for r in roots for p in sorted(pathlib.Path(r).rglob("*.kt")))
    print(f"{'would delete' if dry else 'deleted'} {total} comment lines", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1:])
