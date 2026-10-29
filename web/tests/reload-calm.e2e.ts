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
