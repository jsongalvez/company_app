import assert from "node:assert/strict";
import { test } from "node:test";
import { rolesToSave } from "../../src/lib/roles-to-save";

// As /api/roles lists them (Superuser is never listed).
const roles = [
    ...["OWNER", "MANAGER", "COORDINATOR", "PRACTITIONER", "ACCOUNTANT"].map((name) => ({ name, assignable: true, noAccess: false })),
    { name: "ONBOARDING", assignable: false, noAccess: true },
];

test("taking Practitioner off a Superuser keeps Superuser", () => {
    assert.deepEqual(rolesToSave([], ["SUPERUSER", "PRACTITIONER"], roles), ["SUPERUSER"]);
});

test("nothing ticked on a plain user saves the no-access role", () => {
    assert.deepEqual(rolesToSave([], ["PRACTITIONER"], roles), ["ONBOARDING"]);
});

test("ticked roles are saved as picked", () => {
    assert.deepEqual(rolesToSave(["MANAGER"], ["ONBOARDING", "PRACTITIONER"], roles), ["MANAGER"]);
});
