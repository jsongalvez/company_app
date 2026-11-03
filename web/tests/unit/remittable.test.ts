import assert from "node:assert/strict";
import { test } from "node:test";
import { remittable } from "../../src/lib/remittable";

test("a ₱0 item has nothing to remit, so it isn't offered", () => {
    const free = { amount: "0.00" };
    const paid = { amount: "500.00" };
    assert.deepEqual(
        remittable([free, paid], (i) => i.amount),
        [paid],
    );
});
