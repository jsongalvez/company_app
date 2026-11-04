#!/usr/bin/env python3
"""Wrap each function detekt's TracedAtDefinition rule flags in traced("Owner.function") { ... }.

Usage:
    ./gradlew :backend:detekt 2>&1 | grep TracedAtDefinition > /tmp/traced.txt
    python3 tools/quality/wrap-traced.py /tmp/traced.txt
    python3 tools/quality/wrap-traced.py --hoist FILE...   # only the guard/return pass
    ./gradlew :backend:ktlintFormat

Reads `path:line:col: ... traced("Name") ... [TracedAtDefinition]` lines. Relies on ktlint layout:
a block body closes with `}` at the function's own indentation, and an expression body's lines sit
deeper than the `fun` line. Leading guard clauses move above the span (detekt exempts guards only at
the top of a function) and returns inside it become `return@traced`. Adds the `traced` import where the file lacks it (ktlintFormat sorts it). Prints what it skipped.
"""

import re
import sys
from collections import defaultdict

FINDING = re.compile(r'^(?P<path>[^:]+\.kt):(?P<line>\d+):\d+: .*?traced\(\\?"(?P<name>[\w.]+)\\?"\)')
IMPORT = "import com.companyb.companyapp.observability.traced"


def indent_of(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def signature_end(text: str, start: int):
    """From the function name, find the body opener: ('=' or '{', offset, has_return_type)."""
    i, depth, n = start, 0, len(text)
    seen_params = False
    params_end = None
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            i = j + 3
            continue
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
            continue
        if c == "'":
            i += 2 if text[i + 1] != "\\" else 3
            while i < n and text[i] != "'":
                i += 1
            i += 1
            continue
        if text.startswith("//", i):
            i = text.find("\n", i)
            continue
        if c in "([<" and not (c == "<" and text[i - 1] == " "):
            depth += 1
        elif c in ")]>" and not (c == ">" and text[i - 1] in "-="):
            depth -= 1
            if depth == 0 and c == ")" and not seen_params:
                seen_params = True
                params_end = i
        elif depth == 0 and seen_params and c == "{":
            return "{", i, ":" in text[params_end:i]
        elif depth == 0 and seen_params and c == "=" and text[i + 1] != "=" and text[i - 1] not in "!<>=":
            return "=", i, ":" in text[params_end:i]
        i += 1
    return None


def wrap(lines: list[str], idx: int, name: str) -> bool:
    fun_line = lines[idx]
    m = re.search(r"\bfun\b", fun_line)
    if not m:
        return False
    base = indent_of(fun_line)
    text = "\n".join(lines[idx:])
    found = signature_end(text, m.start())
    if not found:
        return False
    kind, offset, has_return = found
    opener_line = idx + text[:offset].count("\n")
    col = offset - (text.rfind("\n", 0, offset) + 1)
    head = lines[opener_line][: col + 1]
    rest = lines[opener_line][col + 1 :].strip()
    pad = " " * base
    if kind == "{":
        end = next(k for k in range(opener_line + 1, len(lines)) if indent_of(lines[k]) == base and lines[k].strip().startswith("}"))
        if rest:
            return False
        body = [(" " * 4 + l) if l.strip() else l for l in lines[opener_line + 1 : end]]
        opening = f'{pad}    {"return " if has_return else ""}traced("{name}") {{'
        lines[opener_line : end + 1] = [head, opening, *body, f"{pad}    }}", lines[end]]
        return True
    end = opener_line
    for k in range(opener_line + 1, len(lines)):
        closes = indent_of(lines[k]) == base and lines[k].strip()[:1] in "})]."
        if lines[k].strip() and indent_of(lines[k]) <= base and not (rest and closes):
            break
        if lines[k].strip():
            end = k
    expr = ([pad + rest] if rest else []) + lines[opener_line + 1 : end + 1]
    first = next(l for l in expr if l.strip())
    shift = base + 8 - indent_of(first)
    moved = [(" " * shift + l if shift >= 0 else l[-shift:]) if l.strip() else l for l in expr]
    lines[opener_line : end + 1] = [head.rstrip(), f'{pad}    traced("{name}") {{', *moved, f"{pad}    }}"]
    return True


OPENER = re.compile(r'^(?P<pad> *)(?P<ret>return )?traced\("[\w.]+"\) \{$')
GUARD_CALL = re.compile(r"^(require|check|requireNotNull|checkNotNull)\(")
ELVIS_EXIT = re.compile(r"\?: (return|throw)\b")
RETURN = re.compile(r"\breturn\b(?![@\w])")


def statement_end(lines: list[str], start: int, pad: int) -> int:
    end = start
    for k in range(start + 1, len(lines)):
        if lines[k].strip() and indent_of(lines[k]) <= pad and lines[k].strip()[:1] not in "})].?":
            break
        if lines[k].strip() and indent_of(lines[k]) < pad:
            break
        end = k
    return end


def is_guard(stmt: list[str]) -> bool:
    text = re.sub(r'"(?:[^"\\]|\\.)*"', '""', " ".join(l.strip() for l in stmt))
    first = stmt[0].strip()
    if GUARD_CALL.match(first):
        return True
    if first.startswith("val ") and ELVIS_EXIT.search(stmt[-1]):
        return True
    if not first.startswith("if (") or re.search(r"\belse\b", text):
        return False
    inner = text[text.find(")") :]
    return bool(re.search(r"\)\s*(\{\s*)?(return|throw)\b[^{}]*\}?\s*$", text)) and inner.count("{") <= 1


def hoist(lines: list[str]) -> int:
    """In each `traced` block body: move leading guard clauses above it, label its returns."""
    moved = 0
    i = 0
    while i < len(lines):
        m = OPENER.match(lines[i])
        if not m or not lines[i - 1].rstrip().endswith("{"):
            i += 1
            continue
        pad = len(m["pad"])
        close = next(k for k in range(i + 1, len(lines)) if indent_of(lines[k]) == pad and lines[k].strip() == "}")
        start = i + 1
        guards = []
        while start < close:
            end = statement_end(lines, start, pad + 4)
            stmt = lines[start : end + 1]
            if not is_guard(stmt):
                break
            guards += [l[4:] if l.strip() else l for l in stmt]
            start = end + 1
        for k in range(start, close):
            lines[k] = RETURN.sub("return@traced", lines[k])
        lines[i + 1 : close] = lines[start:close]
        lines[i:i] = guards
        moved += len(guards)
        i += len(guards) + 1
    return moved


def add_import(lines: list[str]) -> None:
    if IMPORT in lines or any(l.startswith("package com.companyb.companyapp.observability") for l in lines):
        return
    imports = [i for i, l in enumerate(lines) if l.startswith("import ")]
    at = imports[-1] + 1 if imports else next(i for i, l in enumerate(lines) if l.startswith("package ")) + 2
    lines.insert(at, IMPORT)


def main(report: str) -> int:
    if report == "--hoist":
        total = 0
        for path in sys.argv[2:]:
            before = open(path, encoding="utf-8").read()
            lines = before.split("\n")
            total += hoist(lines)
            if "\n".join(lines) != before:
                open(path, "w", encoding="utf-8").write("\n".join(lines))
        print(f"hoisted {total} guard lines")
        return 0
    by_file = defaultdict(list)
    for raw in open(report, encoding="utf-8"):
        m = FINDING.match(raw.strip())
        if m:
            by_file[m["path"]].append((int(m["line"]), m["name"]))
    done = skipped = 0
    for path, items in by_file.items():
        lines = open(path, encoding="utf-8").read().split("\n")
        for line, name in sorted(set(items), reverse=True):
            if wrap(lines, line - 1, name):
                done += 1
            else:
                skipped += 1
                print(f"skipped {path}:{line} {name}")
        hoist(lines)
        add_import(lines)
        open(path, "w", encoding="utf-8").write("\n".join(lines))
    print(f"wrapped {done}, skipped {skipped}")
    return 1 if skipped else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
