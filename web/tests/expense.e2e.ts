import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("add an expense for today", { session: "owner" }, async ({ app, screen }) => {
    const note = `Water refill ${Date.now().toString().slice(-6)}`;
    await app.open("/#/finance");
    await screen.getByRole("button", "Add").first().tap();
    const dialog = screen.getByRole("dialog", "Add expense");
    await dialog.getByLabel(/^Category/).selectOption({ value: "WATER" });
    await dialog.getByLabel(/^Amount/).fill("150");
    await dialog.getByLabel(/^Notes/).fill(note);
    await dialog.getByRole("button", "Save").tap();
    await expect(screen.getByRole("row").filter({ hasText: note })).toBeVisible();
});
