import { test } from "@e2e-dev/web";
import { expect } from "e2e";

const savedBuild = async () => (await (await caches.match("/"))?.text())?.match(/index-[\w-]+\.js/)?.[0];

// A reload opens the page the phone saved. When the server had a new build but the phone hadn't saved it yet,
// "Update" reloaded the old build and asked again (seen on a slow hotspot, Oct 29).
test("Update is offered only once the phone holds the new version", { session: "owner" }, async ({ app, screen, browser }) => {
    let checks = 0;
    let newPage = "";
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect.poll(() => browser.evaluate(savedBuild), { timeout: 15_000 }).toMatch(/^index-/);
    await new Promise((r) => setTimeout(r, 1500));
    const origin = await browser.evaluate(() => location.origin);
    newPage = (await (await fetch(origin + "/")).text()).replace(/index-[\w-]+\.js/, "index-NEWBUILD.js");
    await browser.route(/^[^#?]*\/(\?[^#]*)?$/, async (route) => {
        checks++;
        await route.fulfill({ status: 200, headers: { "Content-Type": "text/html" }, body: newPage });
    });

    const before = checks;
    await browser.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
    await expect.poll(() => checks, { timeout: 10_000 }).toBeGreaterThan(before);
    await new Promise((r) => setTimeout(r, 500));
    await expect(screen.getByRole("button", { name: "Update" })).toHaveCount(0);

    await browser.evaluate(async (html) => {
        await (await caches.open("app-shell-v1")).put("/", new Response(html, { headers: { "Content-Type": "text/html" } }));
        document.dispatchEvent(new Event("visibilitychange"));
    }, newPage);
    await expect(screen.getByRole("button", { name: "Update" })).toBeVisible({ timeout: 10_000 });
});
