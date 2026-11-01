import assert from "node:assert/strict";
import { test } from "node:test";
import { rolesToSave } from "../../src/lib/roles-to-save";

const assignable = ["OWNER", "MANAGER", "COORDINATOR", "PRACTITIONER", "ACCOUNTANT"];

test("taking Practitioner off a Superuser keeps Superuser", () => {
    assert.deepEqual(rolesToSave([], ["SUPERUSER", "PRACTITIONER"], assignable), ["SUPERUSER"]);
});

test("nothing ticked on a plain user leaves Onboarding", () => {
    assert.deepEqual(rolesToSave([], ["PRACTITIONER"], assignable), ["ONBOARDING"]);
});

test("ticked roles are saved as picked", () => {
    assert.deepEqual(rolesToSave(["MANAGER"], ["ONBOARDING", "PRACTITIONER"], assignable), ["MANAGER"]);
});
