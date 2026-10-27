import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { addDoneSessionForNewClient, clockInHere, watchReads } from "./support";

// Saving or marking a session cannot change the day's status, so Today does not ask for it again.
// Only the Refresh button does.
test("saving and marking a session do not re-read the branch day", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);

    const reads = await watchReads(browser, { day: /^\/api\/branches\/[^/?]+\/today$/ });

    await addDoneSessionForNewClient(screen);
    expect(reads.day).toBe(0);

    await screen.getByRole("button", "Refresh").tap();
    await expect.poll(() => reads.day).toBe(1);
});

// A Finance save re-reads only the lists it can change (an expense: the expenses and the day's totals).
// Other people's edits show when Finance is opened again or the app reloads.
test("saving an expense re-reads only the expenses and the day's totals", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/finance");
    await expect(screen.getByRole("heading", "Expenses")).toBeVisible();

    const reads = await watchReads(browser, {
        expenses: /^\/api\/expenses\?/,
        pay: /^\/api\/compensations\?/,
        allowances: /^\/api\/allowances\?/,
        shares: /^\/api\/commission-splits\//,
        totals: /^\/api\/branches\/[^/]+\/daily-summary\?/,
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

    const reads = await watchReads(browser, {
        expenses: /^\/api\/expenses\?/,
        pay: /^\/api\/compensations\?/,
        allowances: /^\/api\/allowances\?/,
        shares: /^\/api\/commission-splits\//,
        totals: /^\/api\/branches\/[^/]+\/daily-summary\?/,
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

// A phone on a weak hotspot pays a round trip for every request in a row. Signing in is one request:
// the login answer carries who you are, your rights, branches, the branch's day, your shift, unread alerts and Today's lists.
const OPENING_READS: [string, RegExp][] = [
    ["me", /^\/api\/me$/],
    ["rights", /\/api\/me\/capabilities$/],
    ["my branches", /\/api\/me\/branches$/],
    ["branch list", /\/api\/branches\/accessible$/],
    ["day", /\/api\/branches\/[^/]+\/today$/],
    ["today lists", /\/api\/branches\/[^/]+\/today-screen$/],
    ["shift", /\/api\/me\/active-attendance$/],
    ["unread", /\/api\/notifications\/unread-count$/],
];

const openingReads = (browser: Parameters<typeof watchReads>[0]) =>
    watchReads(browser, Object.fromEntries([...OPENING_READS, ["start", /^\/api\/start(\?|$)/] as [string, RegExp]]));

test("signing in opens the app with one request", async ({ app, screen, browser }) => {
    await app.open("/");
    const reads = await openingReads(browser);
    await screen.getByLabel("Username").fill("owner");
    await screen.getByLabel("Password").fill("owner-dev-pass");
    await screen.getByRole("button", "Sign in").tap();
    await expect(
        screen
            .getByRole("banner")
            .getByRole("button", /Clock (in|out)/)
            .first(),
    ).toBeVisible();
    await expect(screen.getByRole("heading", "Booked visits")).toBeVisible();
    expect(reads).toEqual({ start: 0, me: 0, rights: 0, "my branches": 0, "branch list": 0, day: 0, "today lists": 0, shift: 0, unread: 0 });
});

test("opening the app again asks once for everything", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(
        screen
            .getByRole("banner")
            .getByRole("button", /Clock (in|out)/)
            .first(),
    ).toBeVisible();
    const reads = await openingReads(browser);
    await browser.reload();
    await expect(
        screen
            .getByRole("banner")
            .getByRole("button", /Clock (in|out)/)
            .first(),
    ).toBeVisible();
    await expect.poll(() => reads.start).toBe(1);
    expect(reads).toEqual({ start: 1, me: 0, rights: 0, "my branches": 0, "branch list": 0, day: 0, "today lists": 0, shift: 0, unread: 0 });
});

// Finance opens with about eight lists, asked for at once: none waits for another's answer.
test("opening Finance asks for its lists at once", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(
        screen
            .getByRole("banner")
            .getByRole("button", /Clock (in|out)/)
            .first(),
    ).toBeVisible();
    const sent: string[] = [];
    // Each read is held 1 s before it goes: one that waited for another's answer arrives after the first release.
    let released = false;
    let sentAfterRelease = 0;
    await browser.route(/\/api\//, async (route) => {
        sent.push(`${route.request.method} ${new URL(route.request.url).pathname}`);
        if (released) sentAfterRelease++;
        await new Promise((r) => setTimeout(r, 1000));
        released = true;
        await route.fallback();
    });
    await app.open("/#/finance");
    await expect(screen.getByRole("heading", "Allowances")).toBeVisible();
    await expect(screen.getByRole("heading", "Commission share")).toBeVisible();
    expect(sent.length).toBeGreaterThan(3);
    expect(sent.every((s) => s.startsWith("GET "))).toBe(true);
    expect(sentAfterRelease).toBe(0);
});

// A session and the practitioners saved with it are one request.
test("saving a session sends its practitioners with it", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    const writes: string[] = [];
    let practitioners = 0;
    await browser.route(/\/api\//, async (route) => {
        const { method, url, postData } = route.request;
        const path = new URL(url).pathname;
        if (method === "POST") {
            writes.push(path);
            if (path === "/api/sessions") practitioners = (JSON.parse(postData ?? "{}") as { practitioners?: unknown[] }).practitioners?.length ?? 0;
        }
        await route.fallback();
    });
    await addDoneSessionForNewClient(screen);
    expect(writes.filter((p) => p.endsWith("/practitioners"))).toEqual([]);
    expect(writes.filter((p) => p === "/api/sessions")).toHaveLength(1);
    expect(practitioners).toBeGreaterThan(0);
});

// Branches opens on the person's own branch: its staff and prices are asked for with the list, not after it.
test("opening Branches asks for the list and the branch's staff at once", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    const sent: string[] = [];
    let released = false;
    let sentAfterRelease = 0;
    await browser.route(/\/api\//, async (route) => {
        sent.push(new URL(route.request.url).pathname);
        if (released) sentAfterRelease++;
        await new Promise((r) => setTimeout(r, 1000));
        released = true;
        await route.fallback();
    });
    await app.open("/#/branches");
    await expect(screen.getByRole("heading", "Staff", { exact: false }).first()).toBeVisible({ timeout: 15_000 });
    expect(sent.some((p) => p.endsWith("/assignments"))).toBe(true);
    expect(sentAfterRelease).toBe(0);
});
