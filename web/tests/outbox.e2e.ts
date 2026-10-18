import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// No signal: a front-desk save waits on the phone, then goes out by itself once the server answers.
test("a save made offline is sent when the signal is back", { session: "owner" }, async ({ app, screen, browser }) => {
    const note = `Offline water ${Date.now().toString().slice(-6)}`;
    await app.open("/#/finance");
    await expect(screen.getByRole("heading", "Expenses & pay")).toBeVisible();
    const down = /\/(api\/|health)/;
    await browser.route(down, (route) => route.abort());
    await screen.getByRole("button", "Add").first().tap();
    const dialog = screen.getByRole("dialog", "Add expense");
    await dialog.getByLabel(/^Category/).selectOption({ value: "WATER" });
    await dialog.getByLabel(/^Amount/).fill("150");
    await dialog.getByLabel(/^Notes/).fill(note);
    await dialog.getByRole("button", "Save").tap();
    await expect(screen.getByText("Saved on this phone", { exact: false })).toBeVisible({ timeout: 20_000 });
    await expect(screen.getByText("1 change waiting to send", { exact: false })).toBeVisible();
    await browser.unroute(down);
    await expect(screen.getByText("waiting to send", { exact: false })).not.toBeVisible({ timeout: 20_000 });
    await expect(screen.getByRole("row").filter({ hasText: note })).toBeVisible({ timeout: 15_000 });
});
