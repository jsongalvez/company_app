import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";

// Owed #20: a remitted day refuses members' changes, so its session offers none (roles.sql seeds the day).
test("a practitioner sees no change controls on a remitted day's session", { session: "role-practitioner" }, async ({ app, screen }) => {
    await app.open(`/#/today?date=${businessDate(-3)}`);
    await expect(screen.getByText(/is remitted/)).toBeVisible();
    await screen.getByRole("row").filter({ hasText: "Remit Dayclient" }).tap();
    await expect(screen.getByRole("heading", "Remit Dayclient")).toBeVisible();
    await expect(screen.getByRole("heading", "Change history")).toBeVisible();
    for (const name of ["Void session", "Product sale", "Change", "Done", "No-show"]) {
        await expect(screen.getByRole("button", name), name).toHaveCount(0);
    }
});

// Owed #20: every branch member fixes an earlier day's expense with a reason, and its history names them.
test("a practitioner fixes yesterday's expense and its history shows who, what and why", { session: "role-practitioner" }, async ({ app, screen }) => {
    const note = `Ice${Date.now().toString().slice(-6)}`;
    await app.open(`/#/finance?date=${businessDate(-1)}`);
    await screen.getByRole("button", "Add").first().tap();
    const add = screen.getByRole("dialog", "Add expense");
    await add.getByLabel(/^Amount/).fill("40");
    await add.getByLabel(/^Notes/).fill(note);
    await add.getByRole("button", "Save").tap();
    await expect(add).toHaveCount(0);

    const row = screen.getByRole("row").filter({ hasText: note });
    await row.getByRole("button", "Edit").tap();
    const edit = screen.getByRole("dialog", "Edit expense");
    await edit.getByLabel(/^Amount/).fill("45");
    await edit.getByLabel(/^Reason for change/).fill("Receipt said 45");
    await edit.getByRole("button", "Save").tap();
    await expect(edit).toHaveCount(0);

    await row.getByRole("button", "History").tap();
    const latest = screen.getByRole("row").filter({ hasText: "Receipt said 45" }).last();
    await expect(latest).toContainText("Role Practitioner");
    await expect(latest).toContainText("45");
});
