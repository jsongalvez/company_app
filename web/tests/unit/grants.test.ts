// What the server's grant rows switch on in the web. Rows mirror active_user_capabilities (V1__full_schema.sql):
// owners and managers get ASSIGN_COMPENSATION at GLOBAL, and again at BRANCH only where they are assigned.
// The server refuses a GLOBAL-only holder (FinanceReadBackAuthzTest), so the web must not show them the button.
import { test } from "node:test";
import assert from "node:assert/strict";
import { applies, type Grant } from "../../src/lib/grants";

const NIL = "00000000-0000-0000-0000-000000000000";
const g = (capabilityCode: string, contextType: string, contextId = NIL): Grant => ({ capabilityCode, contextType, contextId });
const can = (rows: Grant[], code: string, branch = "b1", day?: string, today?: string) => rows.some((r) => applies(r, code, branch, day, today));

const unassignedOwner = [
    g("MANAGE_USERS", "GLOBAL"),
    g("ASSIGN_DELEGATE", "GLOBAL"),
    g("ASSIGN_COMPENSATION", "GLOBAL"),
    g("VIEW_BRANCH_DATA", "GLOBAL"),
    g("MANAGE_CATALOG", "GLOBAL"),
];
const assignedOwner = [...unassignedOwner, g("ASSIGN_COMPENSATION", "BRANCH", "b1"), g("EDIT_BRANCH_DATA", "BRANCH", "b1"), g("EDIT_PAST_DAY", "BRANCH", "b1")];
const accountant = [g("VIEW_BRANCH_DATA", "GLOBAL"), g("SUBMIT_REMITTANCE", "BRANCH", "b1")];
const reliefHelper = [g("EDIT_BRANCH_DATA", "BRANCH_DAY", "d1")];

test("an all-branch pay grant does not switch on pay controls", () => {
    assert.equal(can(unassignedOwner, "ASSIGN_COMPENSATION"), false);
});

test("a branch pay grant does, and only at that branch", () => {
    assert.equal(can(assignedOwner, "ASSIGN_COMPENSATION"), true);
    assert.equal(can(assignedOwner, "ASSIGN_COMPENSATION", "b2"), false);
});

test("an all-branch owner still sees every branch and the management pages", () => {
    for (const code of ["VIEW_BRANCH_DATA", "MANAGE_USERS", "ASSIGN_DELEGATE", "MANAGE_CATALOG"])
        assert.equal(can(unassignedOwner, code, "any-branch"), true, code);
});

test("an unassigned owner cannot edit, void or remit", () => {
    for (const code of ["EDIT_BRANCH_DATA", "EDIT_PAST_DAY", "VOID_SESSION", "SUBMIT_REMITTANCE", "MANAGE_PRODUCTS"])
        assert.equal(can(unassignedOwner, code), false, code);
});

test("an accountant reads everywhere and remits only at their branch", () => {
    assert.equal(can(accountant, "VIEW_BRANCH_DATA", "b9"), true);
    assert.equal(can(accountant, "SUBMIT_REMITTANCE"), true);
    assert.equal(can(accountant, "SUBMIT_REMITTANCE", "b2"), false);
    assert.equal(can(accountant, "EDIT_BRANCH_DATA"), false);
});

test("a day grant edits only that branch day", () => {
    assert.equal(can(reliefHelper, "EDIT_BRANCH_DATA", "b1", undefined, "d1"), true);
    assert.equal(can(reliefHelper, "EDIT_BRANCH_DATA", "b1", undefined, "d2"), false);
    assert.equal(can(reliefHelper, "EDIT_BRANCH_DATA", "b1", "d1"), true);
    assert.equal(can(reliefHelper, "EDIT_BRANCH_DATA", "b1", "d2"), false);
});
