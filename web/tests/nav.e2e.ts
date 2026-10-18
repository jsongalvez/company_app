import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback 746a0c70: tapping Alerts again should take you back to the previous page.
test("tapping Alerts again goes back", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/today");
    await screen.getByRole("navigation", "Main").getByRole("link", "Clients").tap();
    await expect(screen.getByRole("heading", "Clients")).toBeVisible();
    await screen.getByRole("link", "Alerts").tap();
    await expect(screen.getByRole("heading", "Alerts")).toBeVisible();
    await screen.getByRole("link", "Close alerts").tap();
    await expect(screen.getByRole("heading", "Clients")).toBeVisible();
});
