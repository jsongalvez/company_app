import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere, clockOutHere, doneSessionForNewClient } from "./support";

// Clocking in or out changes the clock button and what Today allows. The rest of the page stays put:
// Who's clocked in and today's list don't drop to "Loading…" and back while they re-read.
test("clocking out and in doesn't blank Today while it reloads", { session: "owner" }, async ({ app, screen, browser }) => {
    await doneSessionForNewClient(app, screen, browser);
    await browser.evaluate(() => {
        const w = window as unknown as { seenLoading: string[] };
        w.seenLoading = [];
        new MutationObserver(() => {
            const main = document.querySelector("main") ?? document.body;
            if (/Loading…|0 of 0 in/.test(main.textContent ?? "")) w.seenLoading.push(location.hash);
        }).observe(document.body, { subtree: true, childList: true, characterData: true });
    });
    await clockOutHere(screen, browser);
    await expect(screen.getByText(/Clock in at .* to see/)).toBeVisible();
    await clockInHere(screen, browser);
    await expect(screen.getByText("1 of 2 in")).toBeVisible();
    expect(await browser.evaluate(() => (window as unknown as { seenLoading: string[] }).seenLoading.length)).toBe(0);
});
