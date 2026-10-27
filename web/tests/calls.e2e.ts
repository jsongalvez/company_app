import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { addDoneSessionForNewClient, clockInHere } from "./support";

// Saving or marking a session cannot change the day's status, so Today does not ask for it again.
// Only the Refresh button does.
test("saving and marking a session do not re-read the branch day", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);

    const dayReads = /\/api\/branches\/[^/]+\/today$/;
    let reads = 0;
    await browser.route(dayReads, async (route) => {
        if (route.request.method === "GET") reads++;
        await route.fallback();
    });

    await addDoneSessionForNewClient(screen);
    expect(reads).toBe(0);

    await screen.getByRole("button", "Refresh").tap();
    await expect.poll(() => reads).toBe(1);
});

// A Finance save re-reads only the lists it can change (an expense: the expenses and the day's totals).
// Other people's edits show when Finance is opened again or the app reloads.
test("saving an expense re-reads only the expenses and the day's totals", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/finance");
    await expect(screen.getByRole("heading", "Expenses")).toBeVisible();

    const reads: Record<string, number> = { expenses: 0, pay: 0, allowances: 0, shares: 0, totals: 0 };
    const lists: [string, RegExp][] = [
        ["expenses", /\/api\/expenses\?/],
        ["pay", /\/api\/compensations\?/],
        ["allowances", /\/api\/allowances\?/],
        ["shares", /\/api\/commission-splits\//],
        ["totals", /\/api\/branches\/[^/]+\/daily-summary\?/],
    ];
    for (const [name, url] of lists)
        await browser.route(url, async (route) => {
            if (route.request.method === "GET") reads[name]++;
            await route.fallback();
        });

    const note = `Calls ${Date.now().toString().slice(-6)}`;
    await screen.getByRole("button", "Add").first().tap();
    const dialog = screen.getByRole("dialog", "Add expense");
    await dialog.getByLabel(/^Category/).selectOption({ value: "WATER" });
    await dialog.getByLabel(/^Amount/).fill("10");
    await dialog.getByLabel(/^Notes/).fill(note);
    await dialog.getByRole("button", "Save").tap();
    await expect(screen.getByRole("row").filter({ hasText: note })).toBeVisible();

    await expect.poll(() => reads.expenses).toBe(1);
    await expect.poll(() => reads.totals).toBe(1);
    expect(reads.pay).toBe(0);
    expect(reads.allowances).toBe(0);
    expect(reads.shares).toBe(0);
});

test("adding an allowance re-reads only the allowances", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/finance");
    await expect(screen.getByRole("heading", "Allowances")).toBeVisible();

    const reads: Record<string, number> = { expenses: 0, pay: 0, allowances: 0, shares: 0, totals: 0 };
    const lists: [string, RegExp][] = [
        ["expenses", /\/api\/expenses\?/],
        ["pay", /\/api\/compensations\?/],
        ["allowances", /\/api\/allowances\?/],
        ["shares", /\/api\/commission-splits\//],
        ["totals", /\/api\/branches\/[^/]+\/daily-summary\?/],
    ];
    for (const [name, url] of lists)
        await browser.route(url, async (route) => {
            if (route.request.method === "GET") reads[name]++;
            await route.fallback();
        });

    await screen.getByRole("button", "Add").nth(2).tap();
    const dialog = screen.getByRole("dialog", "Add allowance");
    await dialog.getByLabel(/^Person/).selectOption({ index: 1 });
    await dialog.getByLabel(/^Amount/).fill("25");
    await dialog.getByRole("button", "Save").tap();

    await expect.poll(() => reads.allowances).toBe(1);
    expect(reads.expenses).toBe(0);
    expect(reads.pay).toBe(0);
    expect(reads.shares).toBe(0);
    expect(reads.totals).toBe(0);
});
