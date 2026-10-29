import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Owed #14: a phone with nothing saved downloads the app's code before it can show Today. On a weak hotspot
// every 10 KB is about 0.1 s, so Today must not wait for the other screens' code.
test("opening Today downloads only the code Today needs", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    const kb = await browser.evaluate(
        () =>
            performance
                .getEntriesByType("resource")
                .filter((r) => /\/assets\/.*\.js$/.test(r.name))
                .reduce((s, r) => s + (r as PerformanceResourceTiming).decodedBodySize, 0) / 1024,
    );
    console.log(`script before Today shows: ${Math.round(kb)} KB`);
    expect(kb).toBeLessThan(200);
});

// The other screens still open with no signal: the service worker saves their files after Today opens.
test("after Today opens, the phone holds every other screen's code", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    const saved = () =>
        browser.evaluate(async () => {
            await navigator.serviceWorker.ready;
            const keys = await (await caches.open("app-shell-v1")).keys();
            return keys.map((k) => new URL(k.url).pathname);
        });
    const screens = [
        "Clients",
        "ClientDetail",
        "SessionDetail",
        "Inventory",
        "Finance",
        "Reports",
        "Remittances",
        "RemittanceDetail",
        "Relief",
        "Notifications",
        "Users",
        "Branches",
        "Catalog",
        "FeedbackList",
        "Profile",
        "MyHistory",
        "Audit",
    ];
    await expect
        .poll(
            async () => {
                const files = await saved();
                return screens.filter((s) => !files.some((f) => new RegExp(`/assets/${s}-[\\w-]+\\.js$`).test(f)));
            },
            { timeout: 15_000 },
        )
        .toEqual([]);
});
