import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

// Security audit M1: the server now sends a Content-Security-Policy. Anything it blocks would break the app
// quietly (the saved-screen and theme scripts run inline before the app), so every violation fails here.
test("signing in, Today and a refresh from the saved screen break no security policy", { session: "owner" }, async ({ app, screen, browser }) => {
    await browser.addInitScript(() => {
        addEventListener("securitypolicyviolation", (e) => {
            const seen = JSON.parse(sessionStorage.getItem("csp") ?? "[]");
            seen.push(`${e.violatedDirective} ${e.blockedURI} ${e.sourceFile}:${e.lineNumber}`);
            sessionStorage.setItem("csp", JSON.stringify(seen));
        });
    });
    const name = await doneSessionForNewClient(app, screen, browser);
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    await browser.evaluate(() => location.reload());
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    expect(await browser.evaluate(() => document.documentElement.dataset.theme)).toMatch(/^(light|dark)$/);
    expect(await browser.evaluate(() => sessionStorage.getItem("csp") ?? "[]")).toBe("[]");
});
