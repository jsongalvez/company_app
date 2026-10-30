import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";

// Feedback 7a765962: "I need a way to take me back to the sessions tab apart from swiping back or going back via the
// browser back button."
test("Expenses & pay has a link back to Sessions for the same day", { session: "owner" }, async ({ app, screen, browser }) => {
    const yesterday = businessDate(-1);
    await app.open(`/#/finance?date=${yesterday}`);
    await screen.getByRole("link", "‹ Sessions").tap();
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    expect(new URL(await browser.url()).hash).toBe(`#/today?date=${yesterday}`);
});
