// What the server's grant rows switch on in the web. Rows mirror active_user_capabilities (V22): owners
// (and superusers) get ASSIGN_COMPENSATION at GLOBAL and pay at every branch (owed #25: "Switching branches to
// pay them sounds cumbersome"); managers pay only where they are assigned. Commission is never an all-branch
// right: only a branch's members see it (owed #26).
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
const accountant = [g("VIEW_BRANCH_DATA", "GLOBAL"), g("SUBMIT_REMITTANCE", "BRANCH", "b1")];
const reliefHelper = [g("EDIT_BRANCH_DATA", "BRANCH_DAY", "d1")];

test("an all-branch pay grant switches on pay controls at every branch", () => {
    assert.equal(can(unassignedOwner, "ASSIGN_COMPENSATION"), true);
    assert.equal(can(unassignedOwner, "ASSIGN_COMPENSATION", "b2"), true);
});

test("an all-branch commission grant shows no commission", () => {
    assert.equal(can([g("VIEW_COMMISSION", "GLOBAL")], "VIEW_COMMISSION"), false);
    assert.equal(can([g("VIEW_COMMISSION", "BRANCH", "b1")], "VIEW_COMMISSION"), true);
    assert.equal(can([g("VIEW_COMMISSION", "BRANCH", "b1")], "VIEW_COMMISSION", "b2"), false);
});

test("a branch pay grant pays only at that branch", () => {
    const manager = [g("ASSIGN_COMPENSATION", "BRANCH", "b1")];
    assert.equal(can(manager, "ASSIGN_COMPENSATION"), true);
    assert.equal(can(manager, "ASSIGN_COMPENSATION", "b2"), false);
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

// Feedback 88baaf43: a coordinator or practitioner holds EDIT_BRANCH_DATA at their branch (V2 seed), which is what
// the server's price and commission routes ask for, but no user-management grant, so Branches is closed to them.
// Branch prices and commission therefore live on Stock, gated on the same grant as the server.
test("a branch worker can change branch prices and commission from Stock but cannot open Branches", () => {
    const worker = [g("VIEW_BRANCH_DATA", "BRANCH", "b1"), g("EDIT_BRANCH_DATA", "BRANCH", "b1")];
    assert.equal(can(worker, "EDIT_BRANCH_DATA"), true);
    assert.equal(can(worker, "EDIT_BRANCH_DATA", "b2"), false);
    assert.equal(can(worker, "MANAGE_USERS"), false);
    assert.equal(can(worker, "ASSIGN_DELEGATE"), false);
});
