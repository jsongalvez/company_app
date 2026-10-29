import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback 31eff8af, 333d545d: the first Sales reports or Expenses & pay of the day sat on "Loading…"
// for a whole round trip, because nothing for that day was saved yet. People take ~0.9s (p10 0.56s, n=171)
// between opening the menu and tapping a page, so the menu fetches what a page has never shown.
for (const [link, heading] of [
    ["Sales reports", "Sales reports"],
    ["Expenses & pay", "Expenses & pay"],
] as const) {
    test(`${link} opened from the phone menu shows no loading line on a slow connection`, { session: "owner" }, async ({ app, screen, browser }) => {
        await browser.setViewport({ width: 392, height: 718 });
        await app.open("/#/today");
        await expect(screen.getByRole("button", "Menu")).toBeVisible();
        await browser.route(/\/api\//, async (route) => {
            if (route.request.method === "GET") await new Promise((r) => setTimeout(r, 300));
            await route.fallback();
        });
        await browser.evaluate(() => {
            const w = window as unknown as { seenLoading: number };
            w.seenLoading = 0;
            new MutationObserver(() => {
                const main = document.querySelector("main") ?? document.body;
                if (/Loading…|Slow connection…/.test(main.textContent ?? "")) w.seenLoading++;
            }).observe(document.body, { subtree: true, childList: true, characterData: true });
        });
        await screen.getByRole("button", "Menu").tap();
        await new Promise((r) => setTimeout(r, 800));
        await screen.getByRole("navigation", "Main").getByRole("link", link).tap();
        await expect(screen.getByRole("heading", heading)).toBeVisible();
        await new Promise((r) => setTimeout(r, 1200));
        expect(await browser.evaluate(() => (window as unknown as { seenLoading: number }).seenLoading)).toBe(0);
    });
}
