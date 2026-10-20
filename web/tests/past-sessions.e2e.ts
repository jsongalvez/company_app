import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback 3578107f: a past day's sessions are listed on Expenses & pay and open for correcting.
// Needs a session on the previous day; CI seeds one (browser.yml "Seed a past day").
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
    // Feedback 5cb3013b: a past day's change needs a reason, then goes through.
    await screen.getByLabel(/^Reason for changes \(required/).fill("Wrong price entered");
    await screen.getByRole("button", "Change").first().tap();
    await screen.getByRole("dialog", "Change price").getByLabel("Final price").fill("1234");
    await screen.getByRole("dialog", "Change price").getByRole("button", "Save").tap();
    await expect(screen.getByText(/1,234/).first()).toBeVisible();
    // Feedback d70d691f: Back returns to the same past day, not to today.
    await screen.getByRole("button", "‹ Back").tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect(screen.getByRole("button", "Today")).not.toBeDisabled();
});
