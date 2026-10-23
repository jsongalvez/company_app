import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

// Feedback 746a0c70: tapping Alerts again should take you back to the previous page.
test("tapping Alerts again goes back", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/today");
    await screen.getByRole("navigation", "Main").getByRole("link", "Clients").tap();
    await expect(screen.getByRole("heading", "Clients")).toBeVisible();
    await screen.getByRole("banner").getByRole("link", "Alerts").tap();
    await expect(screen.getByRole("heading", "Alerts")).toBeVisible();
    await screen.getByRole("banner").getByRole("link", "Close alerts").tap();
    await expect(screen.getByRole("heading", "Clients")).toBeVisible();
});

// Feedback 3bf3842d: Home appeared or vanished as Alerts opened, so the Alerts button
// jumped between taps. It must stay put, coming from Today or from another page.
for (const from of ["today", "clients"]) {
    test(`the Alerts button stays put when tapped from ${from} on a phone`, { session: "owner" }, async ({ app, screen, browser }) => {
        await browser.setViewport({ width: 392, height: 718 });
        await app.open(`/#/${from}`);
        const alerts = () =>
            screen
                .getByRole("banner")
                .getByRole("link", /alerts/i)
                .boundingBox();
        const before = await alerts();
        await screen.getByRole("banner").getByRole("link", "Alerts").tap();
        await expect(screen.getByRole("heading", "Alerts")).toBeVisible();
        expect(await screen.getByRole("banner").getByRole("link", "Home").count()).toBe(0);
        const open = await alerts();
        expect(open!.x).toBe(before!.x);
        expect(open!.y).toBe(before!.y);
        await screen.getByRole("banner").getByRole("link", "Close alerts").tap();
        expect((await alerts())!.x).toBe(before!.x);
    });
}

// Feedback e130b3be: Clients lists the most recent clients before anyone types.
test("Clients lists the client just seen, before any search", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await app.open("/#/clients");
    await expect(screen.getByText("Recent clients")).toBeVisible();
    await expect(screen.getByRole("row").nth(1)).toContainText(name);
});
