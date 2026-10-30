import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Feedback d6cc83cf: a client left Waiting on an earlier day (entered late, or never marked) can
// still be added today, and Today names that day so the branch marks it.
test("a client still waiting yesterday is added today, and Today points to yesterday", { session: "role-practitioner" }, async ({ app, screen, browser }) => {
    const first = `Stale${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("button", "Today")).toBeVisible();

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
    await expect(dialog).toHaveCount(0);

    await screen.getByRole("button", "Today").tap();
    const notice = screen.getByRole("status").filter({ hasText: "Still waiting" });
    await expect(notice).toBeVisible();

    await clockInHere(screen, browser);
    await screen.getByRole("button", "New session").tap();
    const again = screen.getByRole("dialog", /^New session/);
    await again.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await again
        .getByRole("row")
        .filter({ hasText: `${first} Tester` })
        .tap();
    await again.getByRole("button", "Add session").tap();
    await expect(again).toHaveCount(0);
    await expect(screen.getByRole("row").filter({ hasText: `${first} Tester` })).toBeVisible();

    await notice.getByRole("button").first().tap();
    await expect(screen.getByRole("button", "Today")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: `${first} Tester` })).toBeVisible();
});
