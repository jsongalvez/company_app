import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere, clockOutHere, doneSessionForNewClient } from "./support";

test("log a session for a new client and mark it done", { session: "owner" }, async ({ app, screen, browser }) => {
    await doneSessionForNewClient(app, screen, browser);
});

// Feedback 4b9204c1: after clocking out the list stayed, and each reload flashed it before clearing.
test("clocking out takes today's list off the screen and off the phone", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    const off = await browser.onDialog("accept");
    await screen
        .getByRole("banner")
        .getByRole("button", /Clock out/)
        .first()
        .tap();
    await off();
    await expect(screen.getByRole("banner").getByRole("button", "Clock in").first()).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: name })).toHaveCount(0);

    // Nothing of today's list is kept on the phone, so a reload has nothing to flash.
    await expect
        .poll(() =>
            browser.evaluate(() =>
                Object.keys(localStorage)
                    .filter((k) => k.startsWith("qcache:"))
                    .map((k) => localStorage.getItem(k) ?? "")
                    .join(),
            ),
        )
        .not.toContain(name);
    await app.open("/#/today");
    await expect(screen.getByRole("banner").getByRole("button", "Clock in").first()).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: name })).toHaveCount(0);
});

test("a client's page lists their past sessions", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await screen.getByLabel("Find client").fill(name);
    await screen.getByRole("button", "Find").tap();
    await screen.getByRole("link", name).tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Done" }).first()).toBeVisible();
});

// Feedback d75eeba4: a reload went back to "All" instead of the picked tab.
test("the picked Today tab survives a reload", { session: "owner" }, async ({ app, screen, browser }) => {
    await doneSessionForNewClient(app, screen, browser);
    await screen.getByRole("button", /^Done/).first().tap();
    const pressed = () => browser.evaluate(() => document.querySelector('[aria-label="Filter sessions"] [aria-pressed="true"]')?.textContent?.trim() ?? "");
    await expect.poll(pressed).toMatch(/^Done/);
    const hash = await browser.evaluate(() => location.hash);
    expect(hash).toContain("show=COMPLETED");
    await browser.reload();
    await expect.poll(pressed).toMatch(/^Done/);
});

// Feedback 64e93653: the filter stacked each count under its label, so the row
// was two lines tall on a phone.
test("the Today filter stays one compact row on a phone", { session: "owner" }, async ({ app, screen, browser }) => {
    await browser.setViewport({ width: 392, height: 718 });
    await app.open("/#/today");
    const refresh = await screen.getByRole("button", "Refresh").boundingBox();
    const filters = await screen.getByRole("group", "Filter sessions").boundingBox();
    expect(refresh).not.toBeNull();
    expect(filters).not.toBeNull();
    expect(filters!.height).toBeLessThanOrEqual(refresh!.height + 2);
    const tops = await browser.evaluate(() =>
        [...document.querySelectorAll('[aria-label="Filter sessions"] > button')].map((b) => Math.round(b.getBoundingClientRect().top)),
    );
    expect(tops).toHaveLength(5);
    expect(new Set(tops).size).toBe(1);
});

// Feedback 4f48204e: typing in the top search showed no suggestions.
test("the top search suggests clients while typing", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await screen.getByLabel("Find client").fill(name.split(" ")[0]);
    await screen.getByRole("listbox", "Matching clients").getByRole("button", new RegExp(name)).tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Done" }).first()).toBeVisible();
});

// Feedback fb39c00e: a misclick on Clock in or Clock out went straight through.
test("clocking in or out asks first, so a misclick does nothing", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    const bar = screen.getByRole("banner");
    const asked: string[] = [];
    const off = await browser.onDialog(async (d) => {
        asked.push(d.message);
        await d.dismiss();
    });

    // The top button, then the one beside your own name in Who's clocked in.
    await bar
        .getByRole("button", /Clock out/)
        .first()
        .tap();
    await expect.poll(() => asked.length).toBe(1);
    await screen
        .getByRole("button", /^Clock out \S/)
        .first()
        .tap();
    await expect.poll(() => asked.length).toBe(2);
    await expect(bar.getByRole("button", /Clock out/).first()).toBeVisible();
    await off();

    await clockOutHere(screen, browser);
    const off2 = await browser.onDialog(async (d) => {
        asked.push(d.message);
        await d.dismiss();
    });
    await bar.getByRole("button", "Clock in").first().tap();
    await expect.poll(() => asked.length).toBe(3);
    await screen
        .getByRole("button", /^Clock in \S/)
        .first()
        .tap();
    await expect.poll(() => asked.length).toBe(4);
    await expect(bar.getByRole("button", "Clock in").first()).toBeVisible();
    await off2();
});

// Feedback b39f4a9e: clocking in from the top left Today locked until a reload.
test("clocking in from the top bar unlocks Today without a reload", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockOutHere(screen, browser);
    await clockInHere(screen, browser);
    await expect(screen.getByRole("button", "New session")).toBeVisible();
    await expect(screen.getByText(/to see and log today's sessions|to add new sessions/)).toBeHidden();
});

// Feedback cceb50b0: the same from the button beside your own name in Who's clocked in.
test("clocking yourself in from Who's clocked in unlocks Today without a reload", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockOutHere(screen, browser);
    const off = await browser.onDialog("accept");
    await screen
        .getByRole("button", /^Clock in \S/)
        .first()
        .tap();
    await off();
    await expect(
        screen
            .getByRole("banner")
            .getByRole("button", /Clock out/)
            .first(),
    ).toBeVisible();
    await expect(screen.getByRole("button", "New session")).toBeVisible();
    await expect(screen.getByText(/to see and log today's sessions|to add new sessions/)).toBeHidden();
});

// Feedback 4f2d6444: with the "Viewing as" bar showing, the open menu covered the search box.
test("the open menu leaves the search box usable while viewing as someone", async ({ app, screen, browser }) => {
    await browser.setViewport({ width: 392, height: 718 });
    await app.open("/");
    await screen.getByLabel("Username").fill("dev");
    await screen.getByLabel("Password").fill("dev-dev-pass");
    await screen.getByRole("button", "Sign in").tap();
    await screen.getByRole("button", "Menu").tap();
    await screen.getByRole("navigation", "Main").getByRole("link", "Users").tap();
    await screen.getByRole("row").filter({ hasText: "Dev staff" }).getByRole("button", "View as").tap();
    await expect(screen.getByRole("status").filter({ hasText: "Viewing as" })).toBeVisible();

    await screen.getByRole("button", "Menu").tap();
    await expect(screen.getByRole("navigation", "Main")).toBeVisible();
    const topmost = await browser.evaluate(() => {
        const r = document.querySelector<HTMLInputElement>('input[aria-label="Find client"]')!.getBoundingClientRect();
        const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
        return hit?.getAttribute("aria-label") ?? hit?.tagName ?? "";
    });
    expect(topmost).toBe("Find client");
});
