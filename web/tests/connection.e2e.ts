import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Weak signal: short drops are retried quietly; a longer outage shows a bar and the page
// loads again by itself once the server answers.
test("a dropped request is retried without fuss", { session: "owner" }, async ({ app, screen, browser }) => {
    let dropped = 0;
    await browser.route(/\/api\/me\/history/, async (route) => {
        if (route.request.method === "GET" && dropped < 2) {
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
    await expect(screen.getByRole("heading", "Today")).toBeVisible();
    const down = /\/(api\/|health)/;
    await browser.route(down, (route) => route.abort());
    await screen.getByRole("navigation", "Main").getByRole("link", "My shifts & pay").tap();
    await expect(screen.getByText("Weak connection", { exact: false })).toBeVisible({ timeout: 20_000 });
    await browser.unroute(down);
    await expect(screen.getByText("Weak connection", { exact: false })).not.toBeVisible({ timeout: 15_000 });
    await expect(screen.getByRole("heading", "Clock-ins")).toBeVisible({ timeout: 15_000 });
});
