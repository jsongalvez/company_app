import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import type { Locator, Page } from "playwright-core";
import { iPhone } from "./live";

// Staff use iPhones (owner, 2026-11-20). Safari may clear a site's data after seven days unused unless the
// site is on the Home Screen, so the app asks for lasting storage at start and, in Safari, shows a bar
// saying how to add it. CI runs this in WebKit (browser.yml); Firefox has no mobile emulation.

const BASE = process.env.APP_URL ?? "http://localhost:18080";

/** Counts the app's calls to navigator.storage.persist, keeping the browser's own answer. */
function countPersist() {
    const storage = navigator.storage as StorageManager | undefined;
    const own = storage?.persist?.bind(storage);
    const w = window as unknown as { persistCalls: number };
    w.persistCalls = 0;
    if (storage && own)
        storage.persist = () => {
            w.persistCalls++;
            return own();
        };
}

/** Launched from the Home Screen: Safari sets navigator.standalone. */
function fromHomeScreen() {
    Object.defineProperty(Navigator.prototype, "standalone", { get: () => true });
}

/** Playwright's own locators: e2e's expect drives only its own, so wait on their state by polling. */
const shown = (l: Locator, timeout = 15_000) => expect.poll(() => l.isVisible(), { timeout }).toBe(true);
const gone = (l: Locator, timeout = 15_000) => expect.poll(() => l.isVisible(), { timeout }).toBe(false);

async function signIn(page: Page) {
    const signedIn = page
        .getByRole("banner")
        .getByRole("button", { name: /Clock (in|out)/ })
        .first();
    const refused = page.getByText(/^Too many tries/);
    for (let tries = 0; ; tries++) {
        await page.goto(`${BASE}/`);
        await page.getByLabel("Username").fill("role-practitioner");
        await page.getByLabel("Password").fill("staff-dev-pass");
        await page.getByRole("button", { name: "Sign in" }).tap();
        // On a phone the menu folds behind a button; the top bar's clock button says the app is signed in.
        await expect.poll(async () => (await signedIn.isVisible()) || (await refused.isVisible()), { timeout: 15_000 }).toBe(true);
        // The server takes 10 sign-ins a minute from one address; other tests may have used them.
        if (tries > 0 || !(await refused.isVisible())) break;
        await page.waitForTimeout(61_000);
    }
    await shown(signedIn);
}

const skip = process.env.E2E_BROWSER === "firefox" ? "Firefox has no mobile emulation" : undefined;

test("in iPhone Safari the app asks to keep its data and shows how to add it to the Home Screen", { timeout: 150_000, skip }, async () => {
    const phone = await iPhone(countPersist);
    try {
        const page = await phone.newPage();
        await signIn(page);
        expect(await page.evaluate(() => [/iPhone/.test(navigator.userAgent), navigator.maxTouchPoints > 0])).toEqual([true, true]);

        // Asked at start, and the browser answers yes or no rather than throwing.
        await expect.poll(() => page.evaluate(() => (window as unknown as { persistCalls: number }).persistCalls)).toBeGreaterThan(0);
        expect(typeof (await page.evaluate(() => navigator.storage.persisted()))).toBe("boolean");

        const bar = page.getByRole("status").filter({ hasText: "Add to Home Screen" });
        await shown(bar);
        const box = await bar.boundingBox();
        expect(box && box.x >= 0 && box.x + box.width <= page.viewportSize()!.width).toBe(true);

        await bar.getByRole("button", { name: "How" }).tap();
        const how = page.getByRole("dialog", { name: "Install on iPhone or iPad" });
        await shown(how);
        await shown(how.getByText("Add to Home Screen"));
        await how.getByRole("button", { name: "Close", exact: true }).last().tap();
        await gone(how);

        // "Not now" holds after the app is opened again.
        await bar.getByRole("button", { name: "Not now" }).tap();
        await gone(bar);
        await page.reload();
        await shown(
            page
                .getByRole("banner")
                .getByRole("button", { name: /Clock (in|out)/ })
                .first(),
        );
        await gone(page.getByText("Add to Home Screen"));
    } finally {
        await phone.close();
    }
});

test("opened from the iPhone Home Screen the app shows no install bar or button", { timeout: 150_000, skip }, async () => {
    const phone = await iPhone(fromHomeScreen);
    try {
        const page = await phone.newPage();
        await signIn(page);
        await page.waitForTimeout(1_000);
        await gone(page.getByText("Add to Home Screen"));
        expect(await page.getByRole("button", { name: "Install app" }).count()).toBe(0);
    } finally {
        await phone.close();
    }
});
