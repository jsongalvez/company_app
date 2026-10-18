import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback 3578107f: a past day's sessions are listed on Expenses & pay and open for correcting.
test("open a past session from Expenses & pay", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/finance");
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await screen
        .getByRole("row")
        .filter({ hasText: /Waiting|Done|No show|Cancelled/ })
        .first()
        .tap();
    await expect(screen.getByText(/Reason/).first()).toBeVisible();
});
