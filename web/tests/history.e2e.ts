import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { manilaTime } from "./clock";

test("pick a time span on Change history", { session: "owner" }, async ({ app, screen }) => {
    const branch = `History ${Date.now().toString().slice(-6)}`;
    await app.open("/#/branches");
    await screen.getByRole("button", "New branch").tap();
    const nb = screen.getByRole("dialog", "New branch");
    await nb.getByLabel(/^Name/).fill(branch);
    await nb.getByRole("button", "Create").tap();
    await expect(nb).toBeHidden();

    await app.open("/#/audit");
    await screen.getByRole("button", "Any time").tap();
    await screen.getByRole("dialog", "Time span").getByRole("button", "Last 15 min").tap();
    await expect(screen.getByRole("button", "Last 15 min")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Dev owner" }).first()).toBeVisible();

    // Exact ends, down to the second: a window that ended an hour ago holds none of today's test changes.
    await screen.getByRole("button", "Last 15 min").tap();
    const span = screen.getByRole("dialog", "Time span");
    await span.getByLabel("From").fill(manilaTime(-7200));
    await span.getByLabel("To").fill(manilaTime(-3600 * 1.5));
    await span.getByRole("button", "Apply").tap();
    await expect(span).toBeHidden();
    await expect(screen.getByRole("button", / – /)).toBeVisible();

    await screen.getByRole("button", "Clear").tap();
    await expect(screen.getByRole("button", "Any time")).toBeVisible();
});
