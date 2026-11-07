#!/usr/bin/env python3
"""Role names live in the policy table: backend authorization/Policy.kt.

Counts each role name written in code outside that file: a whole upper-case token
such as OWNER or "OWNER", not part of a longer name (SUPERUSER_ROLE) or a hyphenated
log tag (ADD-PRACTITIONER), with comments left out. Launch-era migrations V1-V12 are
never edited and are not counted. A file's count may only go down:

  python3 tools/quality/role-names.py           check against role-names.baseline.json
  python3 tools/quality/role-names.py --write   lower the baseline to today's counts
  python3 tools/quality/role-names.py self-test
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE = ROOT / "tools/quality/role-names.baseline.json"
POLICY = "backend/src/main/kotlin/com/companyb/companyapp/authorization/Policy.kt"
ROLES = ("SUPERUSER", "OWNER", "MANAGER", "COORDINATOR", "PRACTITIONER", "ACCOUNTANT", "ONBOARDING")
TOKEN = re.compile(r"(?<![\w-])(" + "|".join(ROLES) + r")(?![\w-])")
FROZEN_MIGRATION = re.compile(r"V([1-9]|1[0-2])__")


def strip_comments(text: str, suffix: str) -> str:
    if suffix == ".sql":
        return re.sub(r"--[^\n]*", "", text)
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    text = re.sub(r"<!--.*?-->", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    return re.sub(r"(?<![:\"'])//[^\n]*", "", text)


def count(text: str, suffix: str) -> int:
    return len(TOKEN.findall(strip_comments(text, suffix)))


def sources(root: pathlib.Path):
    yield from (root / "backend/src/main/kotlin").rglob("*.kt")
    for path in (root / "backend/src/main/resources/db/migration").glob("V*.sql"):
        if not FROZEN_MIGRATION.match(path.name):
            yield path
    for pattern in ("*.ts", "*.svelte"):
        for path in (root / "web/src").rglob(pattern):
            if path.name != "schema.d.ts":
                yield path


def counts(root: pathlib.Path) -> dict:
    found = {}
    for path in sources(root):
        rel = path.relative_to(root).as_posix()
        if rel == POLICY:
            continue
        n = count(path.read_text(encoding="utf-8"), path.suffix)
        if n:
            found[rel] = n
    return dict(sorted(found.items()))


def problems(now: dict, allowed: dict) -> list:
    return [
        f"{path}: {n} role names, allowed {allowed.get(path, 0)}"
        for path, n in now.items()
        if n > allowed.get(path, 0)
    ]


def self_test() -> int:
    cases = [
        ('val r = "OWNER"', ".kt", 1),
        ("Role.MANAGER in roles", ".kt", 1),
        ("SUPERUSER_ROLE in roles", ".kt", 0),
        ('logger.info { "[ADD-PRACTITIONER] added" }', ".kt", 0),
        ("// only an OWNER may", ".kt", 0),
        ("/** A COORDINATOR\n * or MANAGER */ x", ".kt", 0),
        ("WHERE r.name = 'ACCOUNTANT' -- not ONBOARDING", ".sql", 1),
        ('<!-- OWNER --><option value="OWNER">', ".svelte", 1),
        ('fetch("https://x/OWNER")', ".ts", 1),
    ]
    bad = [(src, want, count(src, sfx)) for src, sfx, want in cases if count(src, sfx) != want]
    for src, want, got in bad:
        print(f"role-names self-test: {src!r} counted {got}, want {want}")
    rising = problems({"a.kt": 2, "b.kt": 1}, {"a.kt": 2})
    if rising != ["b.kt: 1 role names, allowed 0"]:
        print(f"role-names self-test: a new file with a role name was not caught: {rising}")
        bad.append(rising)
    if not bad:
        print(f"role-names self-test: {len(cases) + 1} passed")
    return 1 if bad else 0


def main(argv: list) -> int:
    if argv[1:] == ["self-test"]:
        return self_test()
    now = counts(ROOT)
    allowed = json.loads(BASELINE.read_text()) if BASELINE.exists() else {}
    if argv[1:] == ["--write"]:
        rising = problems(now, allowed) if BASELINE.exists() else []
        if rising:
            print("role-names: refusing to raise the baseline:\n  " + "\n  ".join(rising))
            return 1
        BASELINE.write_text(json.dumps(now, indent=2) + "\n")
        print(f"role-names: baseline written, {sum(now.values())} names in {len(now)} files")
        return 0
    found = problems(now, allowed)
    if found:
        print("role-names: role names belong in " + POLICY + ".")
        print("Name an action from the policy table instead of a role, or read the role from it:")
        print("  " + "\n  ".join(found))
        return 1
    print(f"role-names: OK, {sum(now.values())} names outside the policy table, none new")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
