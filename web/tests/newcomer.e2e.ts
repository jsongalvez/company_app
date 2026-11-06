import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { noteRefusals, refused } from "./support";

// A Practitioner nobody has put in a branch yet (tools/seed/roles.sql) opens the pages the menu gives them.
test("someone with no branch is told why on every page, and nothing they open is refused", { session: "role-newcomer" }, async ({ app, screen, browser }) => {
    await noteRefusals(browser);
    for (const route of ["/#/today", "/#/profile", "/#/catalog", "/#/my-history", "/#/notifications"]) {
        await app.open(route);
        await expect(screen.getByText(/aren't assigned to a branch yet/)).toBeVisible();
    }
    await new Promise((done) => setTimeout(done, 700));
    expect(await refused(browser)).toEqual([]);
});

test("on a phone, Install app sits in the menu's bottom row with Sign out", { session: "role-practitioner" }, async ({ app, screen, browser }) => {
    await browser.setViewport({ width: 392, height: 718 });
    await app.open("/#/catalog");
    await browser.evaluate(() => {
        const e = new Event("beforeinstallprompt", { cancelable: true });
        Object.assign(e, { prompt: async () => undefined, userChoice: Promise.resolve({ outcome: "dismissed" }) });
        window.dispatchEvent(e);
    });
    await screen.getByRole("button", "Menu").tap();
    const nav = screen.getByRole("navigation", "Main");
    await expect(nav.getByRole("button", "Install app")).toBeVisible();
    const tops = await browser.evaluate(() => {
        const nav = document.querySelector("nav[aria-label='Main']")!;
        const top = (text: string) => [...nav.querySelectorAll("button")].find((b) => b.textContent?.trim() === text)!.getBoundingClientRect().top;
        return { install: Math.round(top("Install app")), signOut: Math.round(top("Sign out")) };
    });
    expect(tops.install).toBe(tops.signOut);
});
