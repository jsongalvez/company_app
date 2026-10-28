import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback 3578107f: a past day's sessions are listed on Expenses & pay and open for correcting.
// Needs a session on the previous day; tools/seed/past-day.sql seeds one, in CI and in the verify copy.
test("open a past session from Expenses & pay", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/finance");
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await screen
        .getByRole("row")
        .filter({ hasText: /Waiting|Done|No show|Cancelled/ })
        .first()
        .tap();
    // Feedback 5cb3013b, 9e138f2b: a past day's change asks for its reason on save, then goes through.
    await screen.getByRole("button", "Change").first().tap();
    await screen.getByRole("dialog", "Change price").getByLabel("Final price").fill("1234");
    await screen.getByRole("dialog", "Change price").getByRole("button", "Save").tap();
    const ask = screen.getByRole("dialog", "Reason for change");
    await expect(ask.getByRole("button", "Save")).toBeDisabled();
    await ask.getByLabel("Reason").fill("Wrong price entered");
    await ask.getByRole("button", "Save").tap();
    await expect(screen.getByText(/1,234/).first()).toBeVisible();
    // Feedback d70d691f: Back returns to the same past day, not to today.
    await screen.getByRole("button", "‹ Back").tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    await expect(screen.getByRole("button", "Today")).not.toBeDisabled();
});
