import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// The app's day turns over at 4 AM Manila time.
function opDate(addDays = 0): string {
    const d = new Date(Date.now() - 4 * 3600_000 + addDays * 86400_000);
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Manila" }).format(d);
}

// Feedback e4ec363c: book a future visit without making a session today; any member can change it.
test("book a visit, move it, start a session from it, cancel another", { session: "owner" }, async ({ app, screen, browser }) => {
    const first = `Book${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await clockInHere(screen, browser);

    await screen.getByRole("button", "Book a visit").tap();
    const dialog = screen.getByRole("dialog", "Book a visit");
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "M" });
    await form.getByLabel(/^Age/).fill("41");
    await form.getByRole("button", "Create client").tap();
    await expect(dialog.getByText(`${first} Tester`, { exact: false })).toBeVisible();
    await dialog.getByLabel(/^Day/).fill(opDate(2));
    await dialog.getByLabel(/^Time/).fill("14:30");
    await dialog.getByLabel(/^Note/).fill("Back pain follow-up");
    await dialog.getByRole("button", "Book visit").tap();

    const item = screen.getByRole("listitem").filter({ hasText: `${first} Tester` });
    await expect(item.getByText("2:30 PM", { exact: false })).toBeVisible();
    await expect(item.getByText("Back pain follow-up")).toBeVisible();

    // Move it to today: the list says who changed it, and the history shows the change.
    await item.getByRole("button", "Change").tap();
    const change = screen.getByRole("dialog", "Change booked visit");
    await change.getByLabel(/^Day/).fill(opDate(0));
    await change.getByRole("button", "Save").tap();
    await expect(item.getByText("Today, 2:30 PM")).toBeVisible();
    await expect(item.getByText("changed by", { exact: false })).toBeVisible();
    await item.getByRole("button", "History").tap();
    await expect(item.getByText("Changed", { exact: false }).first()).toBeVisible();

    // It came in today: start the session from the booking; the booking leaves the list.
    await item.getByRole("button", "Start session").tap();
    const ns = screen.getByRole("dialog", "New session");
    await expect(ns.getByText(`${first} Tester`, { exact: false }).first()).toBeVisible();
    await ns.getByRole("button", "Add session").tap();
    await expect(screen.getByRole("row").filter({ hasText: `${first} Tester` })).toBeVisible();
    await expect(item).not.toBeVisible();

    // Book the same client again, then cancel it.
    await screen.getByRole("button", "Book a visit").tap();
    const again = screen.getByRole("dialog", "Book a visit");
    await again.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await again.getByText(`${first} Tester`, { exact: false }).first().tap();
    await again.getByLabel(/^Day/).fill(opDate(5));
    await again.getByRole("button", "Book visit").tap();
    await expect(item).toBeVisible();
    const ok = await browser.onDialog("accept");
    await item.getByRole("button", /^Cancel .* visit$/).tap();
    await expect(item).not.toBeVisible();
    ok();
});
