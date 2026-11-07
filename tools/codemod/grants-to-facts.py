#!/usr/bin/env python3
"""Rewrite backend test fixtures that write grant rows into AccessFixtures calls that ask the policy table.

    python3 tools/codemod/grants-to-facts.py [--write]

Without --write it prints what it would change. Calls it cannot map are listed as HAND with file:line,
and left as they are.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TESTS = ROOT / "backend/src/test/kotlin"
FIXTURES = TESTS / "com/companyb/companyapp/testsupport/fixtures"

# (capability code, context type) -> the Act whose old reach that grant fed. None: no act counted it.
ACT_FOR = {
    ("VIEW_BRANCH_DATA", "GLOBAL"): "SEE_EVERY_BRANCH",
    ("EDIT_BRANCH_DATA", "GLOBAL"): "WORK_ANYWHERE",
    ("MANAGE_USERS", "GLOBAL"): "MANAGE_USERS",
    ("MANAGE_CATALOG", "GLOBAL"): "MANAGE_CATALOG",
    ("ASSIGN_COMPENSATION", "GLOBAL"): "PAY",
    ("ASSIGN_DELEGATE", "GLOBAL"): "ASSIGN_DELEGATE",
    ("ERASE_CLIENT", "GLOBAL"): "ERASE_CLIENT",
    ("MANAGE_PRODUCTS", "GLOBAL"): None,
    ("VOID_SESSION", "GLOBAL"): None,
    ("VIEW_BRANCH_DATA", "BRANCH"): "SEE_BRANCH",
    ("EDIT_BRANCH_DATA", "BRANCH"): "WORK_HOME_BRANCH",
    ("MANAGE_PRODUCTS", "BRANCH"): "MANAGE_STOCK",
    ("SUBMIT_REMITTANCE", "BRANCH"): "SUBMIT_REMITTANCE",
    ("EDIT_PAST_DAY", "BRANCH"): "CHANGE_CLOSED_DAY",
    ("ASSIGN_COMPENSATION", "BRANCH"): "PAY",
    ("VOID_SESSION", "BRANCH"): "VOID",
    ("VIEW_COMMISSION", "BRANCH"): "SEE_COMMISSION",
}

GRANT_PARAMS = ["userId", "capabilityCode", "contextType", "contextId", "sourceId", "priority", "validFrom", "validTo"]


def signatures():
    sigs = {}
    for name in ("IdentityFixtures.kt", "BranchWorkforceFixtures.kt"):
        text = (FIXTURES / name).read_text()
        for m in re.finditer(r"fun (grant\w+)\(", text):
            if m.group(1) == "grantCapability":
                continue
            body, _ = balanced(text, m.end())
            params = [p.split(":")[0].strip() for p in split_args(body)]
            inner = re.search(r"grantCapability\(", text[m.end():])
            call, _ = balanced(text, m.end() + inner.end())
            sigs[m.group(1)] = (params, named(split_args(call), GRANT_PARAMS))
    return sigs


def balanced(text, start):
    depth, i = 1, start
    while depth:
        depth += {"(": 1, ")": -1}.get(text[i], 0)
        i += 1
    return text[start : i - 1], i


def split_args(body):
    parts, depth, cur = [], 0, ""
    for ch in body:
        depth += {"(": 1, ")": -1, "{": 1, "}": -1}.get(ch, 0)
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    parts.append(cur)
    return [re.sub(r"\s+", " ", p).strip() for p in parts if p.strip()]


def named(args, params):
    out = {}
    for i, a in enumerate(args):
        m = re.match(r"^(\w+) = (.*)$", a, re.S)
        if m and m.group(1) in params:
            out[m.group(1)] = m.group(2)
        elif i < len(params):
            out[params[i]] = a
    return out


def leaf(expr):
    return expr.rsplit(".", 1)[-1]


def replacement(args):
    """The AccessFixtures call for one grant, '' to delete it, or None when it needs a person."""
    if any(k in args for k in ("priority", "validFrom", "validTo")):
        return None
    user, code, ctx, ctx_id = (args.get(k) for k in ("userId", "capabilityCode", "contextType", "contextId"))
    if not (user and code and ctx) or "CapabilityCodes." not in code:
        return None
    code, ctx = leaf(code), leaf(ctx)
    if ctx == "BRANCH_DAY" and code == "EDIT_BRANCH_DATA":
        return f"AccessFixtures.relief({user}, {ctx_id})"
    if (code, ctx) not in ACT_FOR:
        return None
    act = ACT_FOR[(code, ctx)]
    if act is None:
        return ""
    if ctx == "GLOBAL":
        return f"AccessFixtures.allow({user}, Act.{act})"
    return f"AccessFixtures.allow({user}, Act.{act}, Place.Branch({ctx_id}))"


def rewrite(path, text, sigs, report):
    pattern = re.compile(r"(?:IdentityFixtures\.|BranchWorkforceFixtures\.)?\b(grantCapability|" + "|".join(sigs) + r")\(")
    out, pos, changed = [], 0, False
    for m in pattern.finditer(text):
        if m.start() < pos or re.search(r"fun\s+$", text[max(0, m.start() - 8) : m.start()]):
            continue
        body, end = balanced(text, m.end())
        args = split_args(body)
        name = m.group(1)
        if name == "grantCapability":
            if args and re.match(r"^(capabilityCode = )?(\w+\.)*CapabilityCodes\.|^code\b", args[0]):
                continue
            grant = named(args, GRANT_PARAMS)
        else:
            params, fixed = sigs[name]
            given = named(args, params)
            grant = {**{k: v.replace("branchId", given.get("branchId", "branchId")) for k, v in fixed.items()}, **given}
            grant.pop("branchId", None)
        new = replacement(grant)
        line = text.count("\n", 0, m.start()) + 1
        if new is None:
            report.append(f"HAND {path.relative_to(ROOT)}:{line} {name}({', '.join(args)[:140]})")
            continue
        start = m.start()
        if new == "":
            line_start = text.rfind("\n", 0, start) + 1
            line_end = text.find("\n", end)
            if text[line_start:start].strip() == "" and text[end:line_end].strip() == "":
                start, end = line_start, line_end + 1
        out.append(text[pos:start] + new)
        pos, changed = end, True
        report.append(f"{'DROP' if new == '' else 'MOVE'} {path.relative_to(ROOT)}:{line} -> {new or 'deleted (no act counted it)'}")
    if not changed:
        return text
    text = "".join(out) + text[pos:]
    package = re.search(r"^package (\S+)", text, re.M).group(1)
    needed = ["com.companyb.companyapp.testsupport.fixtures.AccessFixtures"]
    if package != "com.companyb.companyapp.authorization":
        needed += ["com.companyb.companyapp.authorization.Act", "com.companyb.companyapp.authorization.Place"]
    for imp in needed:
        short = imp.rsplit(".", 1)[-1]
        if re.search(rf"\b{short}\b", text.split("\n", 1)[1]) and f"import {imp}\n" not in text and package != imp.rsplit(".", 1)[0]:
            text = re.sub(r"^(package .*\n\n)", rf"\1import {imp}\n", text, count=1, flags=re.M)
    return text


def main():
    write = "--write" in sys.argv
    sigs = signatures()
    report = []
    for path in sorted(TESTS.rglob("*.kt")):
        if path.parent == FIXTURES and path.name in ("IdentityFixtures.kt", "BranchWorkforceFixtures.kt"):
            continue
        text = path.read_text()
        new = rewrite(path, text, sigs, report)
        if write and new != text:
            path.write_text(new)
    print("\n".join(report))
    kinds = [r.split()[0] for r in report]
    print(f"moved {kinds.count('MOVE')}, dropped {kinds.count('DROP')}, by hand {kinds.count('HAND')}")


if __name__ == "__main__":
    main()
