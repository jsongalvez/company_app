import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

type Browser = Parameters<Parameters<typeof test>[1]>[0]["browser"];
type Sample = { t: number; text: string; h: number };

/** Reload and sample the top bar's "Updated" line, timed from the new page's start, until `done` holds. */
async function reloadAndWatch(browser: Browser, done: (s: Sample) => boolean): Promise<Sample[]> {
    const before = await browser.evaluate(() => performance.timeOrigin);
    await browser.evaluate(() => location.reload());
    const seen: Sample[] = [];
    const end = Date.now() + 15_000;
    while (Date.now() < end) {
        const s = await browser
            .evaluate(() => {
                const el = [...document.querySelectorAll<HTMLElement>('[aria-label="Last update"]')].find((e) => e.checkVisibility());
                return {
                    painted: performance.getEntriesByType("paint").length > 0,
                    origin: performance.timeOrigin,
                    t: performance.now(),
                    text: el?.textContent?.replace(/\s+/g, " ").trim() ?? "",
                    h: el?.offsetHeight ?? -1,
                };
            })
            .catch(() => null);
        if (s && s.origin !== before && s.painted && s.text) {
            seen.push({ t: Math.round(s.t), text: s.text, h: s.h });
            if (done(seen.at(-1)!)) return seen;
        }
        await new Promise((r) => setTimeout(r, 15));
    }
    return seen;
}

const UPDATED = /^Updated \d{1,2}:\d{2}/;

// A refresh shows the saved screen at once. The top bar says how old it is, says "Updating…" only when
// the answer is slow, and says when it is current, without moving anything.
test("a slow refresh says Updating… then Updated, without shifting", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    let answered = 0;
    await browser.route(/\/api\/start/, async (route) => {
        await new Promise((r) => setTimeout(r, 2000));
        answered = Date.now();
        await route.fallback();
    });
    let after = 0;
    const seen = await reloadAndWatch(browser, (s) => {
        if (answered && !after && UPDATED.test(s.text)) after = s.t;
        return after > 0;
    });
    const updating = seen.filter((s) => s.text === "Updating…");
    expect(seen[0]!.text, JSON.stringify(seen.slice(0, 3))).toMatch(UPDATED);
    expect(seen.filter((s) => s.t < 250 && s.text === "Updating…")).toHaveLength(0);
    expect(updating.length, JSON.stringify(seen.map((s) => s.text))).toBeGreaterThan(0);
    expect(after).toBeGreaterThan(updating.at(-1)!.t);
    expect(new Set(seen.map((s) => s.h)).size, JSON.stringify(seen.map((s) => s.h))).toBe(1);
});

test("a refresh with no answer says Offline, showing the saved time", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    // The phone saves what it showed shortly after; refresh once it has.
    for (
        let i = 0;
        i < 50 && !(await browser.evaluate((n) => Object.keys(localStorage).some((k) => k.startsWith("qcache") && localStorage[k].includes(n)), name));
        i++
    )
        await new Promise((r) => setTimeout(r, 100));
    await browser.route(/\/(api\/|health)/, (route) => route.abort());
    const seen = await reloadAndWatch(browser, (s) => s.text.startsWith("Offline"));
    expect(seen.at(-1)?.text, JSON.stringify(seen.slice(-3))).toMatch(/^Offline, showing \d{1,2}:\d{2}/);
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
});

test("a fast refresh never says Updating…", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect(screen.getByLabel("Last update")).toHaveText(UPDATED);
    const until = Date.now() + 1500;
    const seen = await reloadAndWatch(browser, () => Date.now() > until);
    expect(seen.map((s) => s.text).filter((t) => t === "Updating…")).toHaveLength(0);
    expect(seen.at(-1)?.text).toMatch(UPDATED);
});
