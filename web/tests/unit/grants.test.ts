// Screens ask the server's own rule: can(act, place) over the Act table the server renders (access.gen.ts).
// REACH_CASES is what the server's Access.contextsFor decides; the web must decide the same for every Act.
// Rows mirror active_user_capabilities (V22): owners and superusers hold pay, users and catalog at GLOBAL.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { ACTS, REACH_CASES, type Act } from "../../src/lib/access.gen";
import { allows, reads, type Grant, type Place } from "../../src/lib/grants";

const ids: Record<string, string> = { all: "00000000-0000-0000-0000-000000000000", b1: "b1", b2: "b2", d1: "d1", d2: "d2" };
const places: Record<string, Place> = { everywhere: {}, b1: { branch: "b1" }, "b1/d1": { branch: "b1", day: "d1" } };
const g = (capabilityCode: string, contextType: string, contextId = ids.all!): Grant => ({ capabilityCode, contextType, contextId });

test("the web decides every Act the way the server's Access.contextsFor does", () => {
    for (const act of Object.keys(ACTS) as Act[]) {
        const { code, reach } = ACTS[act];
        for (const [r, grant, place, counts] of REACH_CASES) {
            if (r !== reach) continue;
            const [type, id] = grant.split(":");
            assert.equal(allows([g(code, type!, ids[id!])], act, places[place]!), counts, `${act} ${grant} at ${place}`);
        }
        assert.equal(allows([g("SOMETHING_ELSE", "GLOBAL")], act, places["b1/d1"]!), false, `${act} other code`);
    }
});

test("every Act's reading Act is an Act", () => {
    for (const [act, { read }] of Object.entries(ACTS)) assert.ok(read in ACTS, `${act} reads as ${read}`);
});

test("screens ask permissions only through can(act) and canRead(act)", () => {
    const files: string[] = [];
    const walk = (d: string) => {
        for (const n of readdirSync(d)) {
            const p = join(d, n);
            if (statSync(p).isDirectory()) walk(p);
            else if (/\.(svelte|ts)$/.test(n)) files.push(p);
        }
    };
    walk(join(process.cwd(), "src"));
    const helper = /\bcan(?!Read\b|Queue\b)[A-Z]\w*\s*\(/;
    const code = /\bcan(?:Read)?\(\s*"([A-Z_]+)"/g;
    const bad: string[] = [];
    for (const f of files) {
        const src = readFileSync(f, "utf8");
        src.split("\n").forEach((line, i) => {
            if (helper.test(line)) bad.push(`${f}:${i + 1} ${line.trim()}`);
        });
        for (const m of src.matchAll(code)) if (!(m[1]! in ACTS)) bad.push(`${f}: can("${m[1]}") names no server Act`);
    }
    assert.deepEqual(bad, []);
});

const unassignedOwner = ["MANAGE_USERS", "ASSIGN_DELEGATE", "ASSIGN_COMPENSATION", "VIEW_BRANCH_DATA", "MANAGE_CATALOG"].map((c) => g(c, "GLOBAL"));
const accountant = [g("VIEW_BRANCH_DATA", "GLOBAL"), g("SUBMIT_REMITTANCE", "BRANCH", "b1")];
const practitioner = [g("VIEW_BRANCH_DATA", "BRANCH", "b1"), g("EDIT_BRANCH_DATA", "BRANCH", "b1"), g("MANAGE_CATALOG", "GLOBAL")];
const relief = [g("EDIT_BRANCH_DATA", "BRANCH_DAY", "d1"), g("VIEW_BRANCH_DATA", "BRANCH_DAY", "d1")];
const b1 = { branch: "b1" };
const today = { branch: "b1", day: "d1" };

test("an all-branch owner pays everywhere and from the all-branches page, but cannot work, void or remit", () => {
    assert.equal(allows(unassignedOwner, "PAY", {}), true);
    assert.equal(allows(unassignedOwner, "PAY", { branch: "b2", day: "d2" }), true);
    for (const act of ["WORK_DAY", "WORK_HOME_BRANCH", "VOID", "SUBMIT_REMITTANCE", "MANAGE_STOCK", "CHANGE_CLOSED_DAY"] as Act[])
        assert.equal(allows(unassignedOwner, act, today), false, act);
    assert.equal(reads(unassignedOwner, "SEE_BRANCH", b1), true, "sees branch prices and reports");
});

test("commission stays with the branch's members and its relief for the day", () => {
    assert.equal(allows([g("VIEW_COMMISSION", "GLOBAL")], "SEE_COMMISSION", today), false);
    assert.equal(allows([g("VIEW_COMMISSION", "BRANCH", "b1")], "SEE_COMMISSION", today), true);
    assert.equal(allows([g("VIEW_COMMISSION", "BRANCH_DAY", "d1")], "SEE_COMMISSION", today), true);
    assert.equal(allows([g("VIEW_COMMISSION", "BRANCH", "b1")], "SEE_COMMISSION", { branch: "b2" }), false);
});

test("an accountant reads remittances everywhere and remits only at their branch", () => {
    assert.equal(reads(accountant, "SUBMIT_REMITTANCE", { branch: "b9" }), true);
    assert.equal(allows(accountant, "SUBMIT_REMITTANCE", b1), true);
    assert.equal(allows(accountant, "SUBMIT_REMITTANCE", { branch: "b2" }), false);
    assert.equal(allows(accountant, "WORK_ANYWHERE", b1), false);
});

test("a practitioner sells and changes prices but leaves restock and counts to stock managers", () => {
    assert.equal(allows(practitioner, "WORK_DAY", today), true);
    assert.equal(allows(practitioner, "WORK_HOME_BRANCH", b1), true);
    assert.equal(allows(practitioner, "MANAGE_STOCK", b1), false);
    assert.equal(allows(practitioner, "MANAGE_USERS", {}), false);
    assert.equal(reads(practitioner, "MANAGE_CATALOG", {}), true);
});

test("relief works only its day and not the branch as a whole", () => {
    assert.equal(allows(relief, "WORK_DAY", today), true);
    assert.equal(allows(relief, "WORK_DAY", { branch: "b1", day: "d2" }), false);
    assert.equal(allows(relief, "WORK_HOME_BRANCH", b1), false);
    assert.equal(reads(relief, "SEE_DAY", today), true);
    assert.equal(reads(relief, "SEE_BRANCH", b1), false, "reports cover the whole branch");
    assert.equal(allows(relief, "WORK_ANYWHERE", {}), true, "clients are shared across branches");
});
