import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Owed #8: staff enter yesterday's sessions and sales the next day. A practitioner (no coordinator
// rights, not clocked in) goes back a day on Today, adds a session and a sale, and both show there.
test("a practitioner adds a session and a sale to yesterday from Today", { session: "role-practitioner" }, async ({ app, screen, browser }) => {
    const first = `Prev${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await browser.evaluate(() => {
        const w = window as unknown as { seenLoading: number };
        w.seenLoading = 0;
        new MutationObserver(() => {
            if (/Loading…/.test(document.querySelector("main")?.textContent ?? "")) w.seenLoading++;
        }).observe(document.body, { subtree: true, childList: true, characterData: true });
    });
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("button", "Today")).toBeVisible();
    await expect(screen.getByRole("button", "New session")).toBeVisible();
    // The day switch keeps the page calm: no "Loading…" flash.
    expect(await browser.evaluate(() => (window as unknown as { seenLoading: number }).seenLoading)).toBe(0);

    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", /^New session on /);
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "F" });
    await form.getByLabel(/^Age/).fill("40");
    await form.getByRole("button", "Create client").tap();
    await expect(dialog.getByLabel(/^Price/)).not.toHaveValue("");
    await dialog.getByRole("button", "Add session").tap();

    const row = screen.getByRole("row").filter({ hasText: `${first} Tester` });
    await expect(row).toBeVisible();
    // Anyone in the branch marks it, with a reason (past-mark.e2e.ts).
    await expect(row.getByRole("button", "Done")).toBeVisible();

    const salesTile = () =>
        browser.evaluate(() => {
            const k = [...document.querySelectorAll(".stats .k")].find((e) => e.textContent === "Product sales");
            return Number((k?.nextElementSibling?.textContent ?? "").replace(/[^0-9.]/g, ""));
        });
    const before = await salesTile();
    await screen.getByRole("button", "Product sale").tap();
    const sale = screen.getByRole("dialog", /^Product sale on /);
    await sale.getByLabel(/^Product/).selectOption({ index: 1 });
    await sale.getByRole("button", "Record sale").tap();
    await expect(sale).toHaveCount(0);
    await expect.poll(salesTile).toBeGreaterThan(before);

    // Still there after a reload, on the same day, and not on today.
    await browser.reload();
    await expect(screen.getByRole("row").filter({ hasText: `${first} Tester` })).toBeVisible();
    await screen.getByRole("button", "Today").tap();
    await expect(screen.getByRole("button", "Today")).toHaveCount(0);
    await expect(screen.getByRole("row").filter({ hasText: `${first} Tester` })).toHaveCount(0);
});
