import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere, clockOutHere } from "./support";

type Browser = Parameters<Parameters<typeof test>[1]>[0]["browser"];

/** After the next accepted "are you sure", note how long until `selector`'s text matches `pattern`. */
async function timeAfterConfirm(browser: Browser, selector: string, pattern: string) {
    await browser.evaluate(
        ([sel, pat]) => {
            const w = window as unknown as { confirmedAt?: number; shownAt?: number };
            delete w.confirmedAt;
            delete w.shownAt;
            const ask = window.confirm.bind(window);
            window.confirm = (m?: string) => {
                const yes = ask(m);
                if (yes) w.confirmedAt = performance.now();
                window.confirm = ask;
                return yes;
            };
            const check = () => {
                if (w.confirmedAt === undefined || w.shownAt !== undefined) return;
                const el = document.querySelector(sel);
                if (el && new RegExp(pat).test(el.textContent ?? "")) w.shownAt = performance.now();
            };
            new MutationObserver(check).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true });
        },
        [selector, pattern],
    );
    return async () => {
        await expect.poll(() => browser.evaluate(() => (window as unknown as { shownAt?: number }).shownAt ?? null)).not.toBeNull();
        return browser.evaluate(() => {
            const w = window as unknown as { confirmedAt: number; shownAt: number };
            return Math.round(w.shownAt - w.confirmedAt);
        });
    };
}

async function slowApi(browser: Browser) {
    await browser.route(/\/api\//, async (route) => {
        await new Promise((r) => setTimeout(r, 600));
        await route.fallback();
    });
}

// Feedback (Oct 29): "way too sluggish... It used to be instant." The clock button waited on ~5
// round trips. It now changes the moment the person confirms; the server catches up behind it.
test("clock in and out from the top bar show at once on a slow connection", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockOutHere(screen, browser);
    await slowApi(browser);
    const bar = screen.getByRole("banner");
    const off = await browser.onDialog("accept");

    let took = await timeAfterConfirm(browser, "header.top", "Clock out");
    await bar.getByRole("button", "Clock in").first().tap();
    expect(await took()).toBeLessThan(100);
    await expect(screen.getByRole("status").filter({ hasText: "Clocked in" })).toBeVisible({ timeout: 10_000 });

    took = await timeAfterConfirm(browser, "header.top", "Clock in");
    await bar
        .getByRole("button", /Clock out/)
        .first()
        .tap();
    expect(await took()).toBeLessThan(100);
    await off();
});

test("your own roster row and the count change at once on a slow connection", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    await expect(screen.getByText("1 of 2 in")).toBeVisible();
    await slowApi(browser);
    const off = await browser.onDialog("accept");
    const took = await timeAfterConfirm(browser, "main", "0 of 2 in");
    // On Today the owner's own roster row is the one clocked in ("1 of 2 in").
    await screen.getByRole("button", /^Clock out (?!of )\S/).tap();
    expect(await took()).toBeLessThan(100);
    await expect(screen.getByRole("banner").getByRole("button", "Clock in")).toBeVisible({ timeout: 300 });
    await off();
    await expect(screen.getByText("0 of 2 in")).toBeVisible({ timeout: 10_000 });
});

test("a refused clock-out puts the row and top bar back", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    await expect(screen.getByText("1 of 2 in")).toBeVisible();
    await browser.route(/\/attendance\/marks/, (route) => route.fulfill({ status: 409, json: { error: "Already clocked out" } }));
    const off = await browser.onDialog("accept");
    // On Today the owner's own roster row is the one clocked in ("1 of 2 in").
    await screen.getByRole("button", /^Clock out (?!of )\S/).tap();
    await expect(screen.getByRole("status").filter({ hasText: "Already clocked out" })).toBeVisible();
    await expect(screen.getByText("1 of 2 in")).toBeVisible();
    await expect(screen.getByRole("banner").getByRole("button", /Clock out/)).toBeVisible();
    await off();
});

test("a clock-in is one request: the answer carries the new shift, rights, day and lists", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockOutHere(screen, browser);
    await expect(screen.getByText("0 of 2 in")).toBeVisible();
    await new Promise((r) => setTimeout(r, 1000));
    const calls: string[] = [];
    await browser.route(/\/api\//, async (route) => {
        const u = new URL(route.request.url);
        if (!u.pathname.startsWith("/api/telemetry")) calls.push(`${route.request.method} ${u.pathname}`);
        await route.fallback();
    });
    const off = await browser.onDialog("accept");
    await screen.getByRole("banner").getByRole("button", "Clock in").first().tap();
    await expect(screen.getByRole("status").filter({ hasText: "Clocked in" })).toBeVisible();
    await expect(screen.getByText("1 of 2 in")).toBeVisible();
    await new Promise((r) => setTimeout(r, 2000));
    await off();
    expect(calls, calls.join(", ")).toEqual(["POST /api/attendance/clock-in"]);
});
