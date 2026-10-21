import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

// Found by the TLA+ model in tools/tla/outbox (see its README). Each test is one counterexample TLC printed.
const down = /\/(api\/|health)/;
const refused = "couldn't be sent";
type Item = { failed?: string };
const saved = (browser: { evaluate: <T>(f: () => T) => Promise<T> }) => browser.evaluate(() => JSON.parse(localStorage.getItem("outbox") ?? "[]") as Item[]);

// Done, then Undo, both with no signal: the phone used to queue "back to Waiting" for a session that
// is still waiting on the server, and the server answered 400 (Waiting -> Waiting is not a move).
test("marking Done then Undo with no signal sends nothing and refuses nothing", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen);
    const row = screen.getByRole("row").filter({ hasText: name });
    await row.getByRole("button", "Undo").tap();
    await expect(row.getByText("Waiting")).toBeVisible();
    await browser.route(down, (route) => route.abort());
    await row.getByRole("button", "Done").tap();
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 20_000 });
    await row.getByRole("button", "Undo").tap();
    await expect(row.getByText("Waiting")).toBeVisible();
    await browser.unroute(down);
    // Everything waiting has been sent or refused.
    await expect.poll(async () => (await saved(browser)).filter((i) => !i.failed).length, { timeout: 40_000 }).toBe(0);
    expect((await saved(browser)).filter((i) => i.failed)).toEqual([]);
    await expect(screen.getByText(refused, { exact: false })).not.toBeVisible();
    await expect(row.getByText("Waiting")).toBeVisible();
});

// The send loop walks the saves that were waiting when it started. A mark replaced while an earlier
// save is still going out used to be sent anyway, and the replacement then clashed with it.
test("undoing a Done that is already on its way keeps the last choice", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen);
    const row = screen.getByRole("row").filter({ hasText: name });
    await row.getByRole("button", "Undo").tap();
    await expect(row.getByText("Waiting")).toBeVisible();
    await browser.route(down, (route) => route.abort());
    await row.getByRole("button", "Done").tap();
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 20_000 });
    await browser.unroute(down);
    // Hold the mark just as the phone sends it, then change our mind.
    let release: () => void = () => {};
    const held = new Promise<void>((r) => (release = r));
    let seen = false;
    const status = /\/api\/sessions\/[^/]+\/status/;
    await browser.route(status, async (route) => {
        if (route.request.method === "PATCH" && !seen) {
            seen = true;
            await held;
        }
        await route.fallback();
    });
    await expect.poll(() => seen, { timeout: 40_000 }).toBe(true);
    await row.getByRole("button", "Undo").tap();
    release();
    await expect.poll(async () => (await saved(browser)).filter((i) => !i.failed).length, { timeout: 40_000 }).toBe(0);
    expect((await saved(browser)).filter((i) => i.failed)).toEqual([]);
    await app.open("/#/today");
    await expect(row.getByText("Waiting")).toBeVisible({ timeout: 15_000 });
});

// A session made with no signal and sent when the signal returned used to drop off #/today if the list
// could not be fetched right then and the page reloaded: the "just sent" row lived in memory for two minutes.
test("a session sent from the phone stays on today's list until the server's list shows it", { session: "owner" }, async ({ screen, app, browser }) => {
    await doneSessionForNewClient(app, screen);
    const fresh = `Gone${Date.now().toString().slice(-6)}`;
    await browser.route(down, (route) => route.abort());
    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", "New session");
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(fresh);
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

    // Signal back for saving, but the list still can't be fetched (weak signal).
    await browser.unroute(down);
    const list = /\/dashboard\/today/;
    await browser.route(list, (route) => route.abort());
    await expect.poll(async () => (await saved(browser)).length, { timeout: 40_000 }).toBe(0);
    await expect(row.getByText("Not sent")).not.toBeVisible();

    await browser.evaluate(() => location.reload());
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible({ timeout: 20_000 });
    await expect(screen.getByRole("row").filter({ hasText: `${fresh} Tester` })).toBeVisible({ timeout: 10_000 });
    await browser.unroute(list);
    await expect(screen.getByRole("row").filter({ hasText: `${fresh} Tester` })).toBeVisible({ timeout: 20_000 });
});
