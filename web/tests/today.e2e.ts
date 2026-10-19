import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

test("log a session for a new client and mark it done", { session: "owner" }, async ({ app, screen }) => {
    await doneSessionForNewClient(app, screen);
});

// Feedback 4b9204c1: after clocking out the list stayed, and each reload flashed it before clearing.
test("clocking out takes today's list off the screen and off the phone", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen);
    const off = await browser.onDialog("accept");
    await screen
        .getByRole("button", /Clock out/)
        .first()
        .tap();
    await off();
    await expect(screen.getByRole("button", "Clock in").first()).toBeVisible();
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
    await expect(screen.getByRole("button", "Clock in").first()).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: name })).toHaveCount(0);
});

test("a client's page lists their past sessions", { session: "owner" }, async ({ app, screen }) => {
    const name = await doneSessionForNewClient(app, screen);
    await screen.getByLabel("Find client").fill(name);
    await screen.getByRole("button", "Find").tap();
    await screen.getByRole("link", name).tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Done" }).first()).toBeVisible();
});

// Feedback d75eeba4: a reload went back to "All" instead of the picked tab.
test("the picked Today tab survives a reload", { session: "owner" }, async ({ app, screen, browser }) => {
    await doneSessionForNewClient(app, screen);
    await screen.getByRole("button", /^Done/).first().tap();
    const pressed = () => browser.evaluate(() => document.querySelector('[aria-label="Filter sessions"] [aria-pressed="true"]')?.textContent?.trim() ?? "");
    await expect.poll(pressed).toMatch(/^Done/);
    const hash = await browser.evaluate(() => location.hash);
    expect(hash).toContain("show=COMPLETED");
    await browser.reload();
    await expect.poll(pressed).toMatch(/^Done/);
});
