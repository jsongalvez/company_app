#!/usr/bin/env python3
"""Rewrite test fixture grant calls (IdentityFixtures.grant*, revokeAllCapabilities) into the facts they stand for.

Usage: grants-to-facts.py CALLS_JSON TEST_ROOT
CALLS_JSON lists each call (file, line, helper, code, ctx, live), from a suite run with grant rows switched off:
a dead call (its test passed without the row) is deleted, a live one becomes a role or a membership.
A call no rule covers gets a `TODO(grants-to-facts)` line to settle by hand; the script prints each one.
"""

import json
import re
import sys
from pathlib import Path

HELPERS = r"(?:IdentityFixtures\.)?(grantCapability|grantManageUsers|grantEditBranchData|grantManageProducts|grantVoidSession|grantAssignCompensation|grantAssignDelegate|grantSubmitRemittance|revokeAllCapabilities)\("

COORDINATOR_AT_BRANCH = {"SUBMIT_REMITTANCE", "MANAGE_PRODUCTS", "EDIT_PAST_DAY", "ASSIGN_COMPENSATION", "RECEIVE_NEXT_APPOINTMENT_ALERTS"}
MEMBER_AT_BRANCH = {"EDIT_BRANCH_DATA", "VOID_SESSION", "VIEW_COMMISSION"}
ROLE_EVERYWHERE = {"VIEW_BRANCH_DATA": "ACCOUNTANT", "MANAGE_USERS": "MANAGER", "MANAGE_CATALOG": "PRACTITIONER", "ASSIGN_COMPENSATION": "OWNER", "ASSIGN_DELEGATE": "MANAGER"}
HELPER_ROLE = {"grantManageUsers": "MANAGER", "grantAssignDelegate": "MANAGER", "grantAssignCompensation": "OWNER"}


def args_of(text):
    parts, depth, cur = [], 0, ""
    for ch in text:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur.strip())
    named = {}
    positional = []
    for p in parts:
        m = re.match(r"^(\w+)\s*=\s*(.+)$", p, re.S)
        if m:
            named[m.group(1)] = m.group(2).strip()
        else:
            positional.append(p)
    return named, positional


def rewrite(call, helper, named, positional):
    user = named.get("userId") or (positional[0] if positional else None)
    if helper == "revokeAllCapabilities":
        return f"IdentityFixtures.revokeAll({user})"
    if helper in HELPER_ROLE:
        return f"IdentityFixtures.hold({user}, Role.{HELPER_ROLE[helper]})"
    if helper == "grantSubmitRemittance":
        branch = named.get("branchId") or positional[2]
        return f"IdentityFixtures.member({user}, {branch}, Role.COORDINATOR)"
    if helper == "grantCapability":
        code = call["code"]
        ctx = call["ctx"]
        at = named.get("contextId") or (positional[3] if len(positional) > 3 else None)
        if ctx == "BRANCH" and code in MEMBER_AT_BRANCH:
            return f"IdentityFixtures.member({user}, {at})"
        if ctx == "BRANCH" and code in COORDINATOR_AT_BRANCH:
            return f"IdentityFixtures.member({user}, {at}, Role.COORDINATOR)"
        if ctx == "GLOBAL" and code in ROLE_EVERYWHERE:
            return f"IdentityFixtures.hold({user}, Role.{ROLE_EVERYWHERE[code]})"
    return None


def main():
    calls = json.load(open(sys.argv[1]))
    root = Path(sys.argv[2])
    by_file = {}
    for c in calls:
        by_file.setdefault(c["file"], []).append(c)
    todo = []
    for file, cs in by_file.items():
        path = root / file
        src = path.read_text()
        lines = {c["line"]: c for c in cs}
        out, i = [], 0
        for m in re.finditer(HELPERS, src):
            line = src.count("\n", 0, m.start()) + 1
            call = lines.get(line)
            if call is None:
                continue
            j, depth = m.end(), 1
            while depth:
                depth += {"(": 1, ")": -1}.get(src[j], 0)
                j += 1
            start = src.rfind("\n", 0, m.start()) + 1
            end = src.find("\n", j)
            indent = re.match(r"\s*", src[start:]).group(0)
            dead = not call["live"] and call.get("intest", True) or (not call["live"] and call["fn"] == "initTestData")
            if dead:
                new = None
            else:
                named, positional = args_of(src[m.end() : j - 1])
                new = rewrite(call, m.group(1), named, positional)
                if new is None:
                    todo.append(f"{file}:{line} {m.group(1)} {call['code']} {call['ctx']}")
                    new = "TODO(grants-to-facts) " + re.sub(r"\s+", " ", src[m.start() : j])
            out.append((start, end, None if new is None else indent + new))
        for start, end, new in reversed(out):
            src = src[:start] + ("" if new is None else new) + src[end + (1 if new is None else 0) :]
        if "Role." in src and "import com.companyb.companyapp.authorization.Role\n" not in src:
            src = re.sub(r"^(package [^\n]+\n\n)", r"\1import com.companyb.companyapp.authorization.Role\n", src, count=1, flags=re.M)
        if "IdentityFixtures." in src and "import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures\n" not in src and "package com.companyb.companyapp.testsupport.fixtures" not in src:
            src = re.sub(r"^(package [^\n]+\n\n)", r"\1import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures\n", src, count=1, flags=re.M)
        path.write_text(src)
    print("\n".join(todo))
    print(f"{len(todo)} calls to settle by hand", file=sys.stderr)


if __name__ == "__main__":
    main()
