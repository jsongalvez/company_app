import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

// Feedback 960bf6c0, d29dd5a5: since the saved app opens at once (#986), a refresh on a stuck
// connection repainted into "Loading…" for ~10s. What the phone saved shows instead, with no loading line.
test("a refresh on a slow connection keeps showing today's saved list", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await browser.route(/\/api\/start/, async (route) => {
        await new Promise((r) => setTimeout(r, 8000));
        await route.fallback();
    });
    await app.open("/#/today");
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible({ timeout: 2000 });
    await expect(screen.getByText(/Loading…|Slow connection…/)).toHaveCount(0);
});

// Feedback (Oct 29): "Before the app stayed still on refresh." A refresh drew an empty page, then the
// app's top bar, then today's lists. The last screen now shows from the first frame, before the script runs.
test("a refresh paints the last screen in its first frame, never an empty page", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    await browser.evaluate(() => location.reload());
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    // An empty first frame shows as a gap between the first paint and the first paint with content.
    const gap = await browser.evaluate(() => {
        const t = (n: string) => performance.getEntriesByName(n)[0]?.startTime ?? NaN;
        return t("first-contentful-paint") - t("first-paint");
    });
    expect(gap).toBe(0);
});

// Owed #14: after an update the new build opened on an empty page, because the saved screen came from the
// old build. It shows anyway, in the old build's styles, until the new app has drawn.
test("the first open of a new version shows the last screen in its first frame", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    await browser.evaluate(() => location.reload());
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    // As the page leaves (after the app saves its screen, on both events), make the saved screen name a build whose files
    // the server no longer has, as after a deploy.
    await browser.evaluate(() => {
        const old = () => {
            const s = JSON.parse(localStorage.getItem("snap")!);
            s.v = s.v.replace(/index-[\w-]+\.js/, "index-OLDBUILD.js");
            s.css = ["/assets/index-OLDBUILD.css"];
            localStorage.setItem("snap", JSON.stringify(s));
        };
        addEventListener("pagehide", old);
        document.addEventListener("visibilitychange", old);
        location.reload();
    });
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    const gap = await browser.evaluate(() => {
        const t = (n: string) => performance.getEntriesByName(n)[0]?.startTime ?? NaN;
        return t("first-contentful-paint") - t("first-paint");
    });
    expect(gap).toBe(0);
    await expect.poll(() => browser.evaluate(() => document.querySelectorAll("#snap, [data-snap-old]").length)).toBe(0);
});
