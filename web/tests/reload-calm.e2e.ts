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
