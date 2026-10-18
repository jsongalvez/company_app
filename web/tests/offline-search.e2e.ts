import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

const down = /\/(api\/|health)/;

// No signal: clients saved on the phone are still found, and a client and session made offline
// show at once (in search and in today's list) before they're sent.
test("offline search finds saved clients and ones made offline", { session: "owner" }, async ({ screen, app, browser }) => {
    const known = await doneSessionForNewClient(app, screen);
    const fresh = `Off${Date.now().toString().slice(-6)}`;
    await browser.route(down, (route) => route.abort());

    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", "New session");
    const search = dialog.getByPlaceholder("Search by name or phone (2+ letters)");
    await search.fill(known.split(" ")[0]!);
    await expect(dialog.getByRole("row").filter({ hasText: known })).toBeVisible({ timeout: 3_000 });

    await search.fill(fresh);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(fresh);
    await form.getByLabel(/^Gender/).selectOption({ value: "M" });
    await form.getByLabel(/^Age/).fill("33");
    await form.getByRole("button", "Create client").tap();
    await expect(dialog.getByText(`${fresh} Tester`, { exact: false })).toBeVisible({ timeout: 5_000 });
    await dialog.getByRole("button", "Add session").tap();

    const row = screen.getByRole("row").filter({ hasText: `${fresh} Tester` });
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 5_000 });

    await screen.getByRole("button", "New session").tap();
    const again = screen.getByRole("dialog", "New session");
    await again.getByPlaceholder("Search by name or phone (2+ letters)").fill(fresh);
    await expect(again.getByRole("row").filter({ hasText: `${fresh} Tester` })).toBeVisible({ timeout: 3_000 });
    await again.getByRole("button", "Cancel").tap();

    await browser.unroute(down);
    await expect(row.getByText("Not sent")).not.toBeVisible({ timeout: 30_000 });
    await expect(row).toBeVisible();
});

// A page never opened before, with no signal: one quiet line, no wall of Retry buttons.
test("offline pages wait quietly instead of showing Retry", { session: "owner" }, async ({ screen, app, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Today")).toBeVisible();
    await browser.route(down, (route) => route.abort());
    await app.open("/#/reports");
    await expect(screen.getByText("Waiting for signal", { exact: false }).first()).toBeVisible({ timeout: 20_000 });
    await expect(screen.getByRole("button", "Retry")).not.toBeVisible();
    await browser.unroute(down);
});
