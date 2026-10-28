import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Weak signal: short drops are retried quietly; a longer outage shows a bar and the page
// loads again by itself once the server answers.
test("a dropped request is retried without fuss", { session: "owner" }, async ({ app, screen, browser }) => {
    let dropped = 0;
    await browser.route(/\/api\/me\/history/, async (route) => {
        if (dropped < 2) {
            dropped++;
            await route.abort();
        } else await route.fallback();
    });
    await app.open("/#/my-history");
    await expect(screen.getByRole("heading", "Clock-ins")).toBeVisible({ timeout: 15_000 });
    await expect(screen.getByText("Weak connection", { exact: false })).not.toBeVisible();
    expect(dropped).toBe(2);
});

test("an outage shows a bar, then recovers by itself", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    const down = /\/(api\/|health)/;
    await browser.route(down, (route) => route.abort());
    await screen.getByRole("navigation", "Main").getByRole("link", "My shifts & pay").tap();
    await expect(screen.getByText("Weak connection", { exact: false })).toBeVisible({ timeout: 20_000 });
    await browser.unroute(down);
    await expect(screen.getByText("Weak connection", { exact: false })).not.toBeVisible({ timeout: 15_000 });
    await expect(screen.getByRole("heading", "Clock-ins")).toBeVisible({ timeout: 15_000 });
});

// The signed-in app is its own file, loaded after the sign-in screen. The phone keeps it too, so the app
// still opens with no signal after a deploy.
test("the phone keeps every app file, the signed-in part included", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    const saved = () =>
        browser.evaluate(async () => {
            await navigator.serviceWorker.ready;
            const keys = await (await caches.open("app-shell-v1")).keys();
            return keys.map((k) => new URL(k.url).pathname).filter((p) => p.startsWith("/assets/"));
        });
    await expect.poll(async () => (await saved()).filter((p) => /\/Shell-[\w-]+\.js$/.test(p)).length, { timeout: 15_000 }).toBe(1);
});
