import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Manila wall-clock time, n seconds from now, as "2026-10-18T14:30:05".
function manila(offsetSec: number) {
    const t = new Date(Date.now() + offsetSec * 1000);
    return new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Manila",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hourCycle: "h23",
    })
        .format(t)
        .replace(" ", "T")
        .replace(/:00$/, ":01"); // the browser drops ":00" seconds, which the fill check rejects
}

test("pick a time span on Change history", { session: "owner" }, async ({ app, screen }) => {
    const branch = `History ${Date.now().toString().slice(-6)}`;
    await app.open("/#/branches");
    await screen.getByRole("button", "New branch").tap();
    const nb = screen.getByRole("dialog", "New branch");
    await nb.getByLabel(/^Name/).fill(branch);
    await nb.getByRole("button", "Create").tap();
    await expect(nb).toBeHidden();

    await app.open("/#/audit");
    await screen.getByRole("button", "Any time").tap();
    await screen.getByRole("dialog", "Time span").getByRole("button", "Last 15 min").tap();
    await expect(screen.getByRole("button", "Last 15 min")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Dev owner" }).first()).toBeVisible();

    // Exact ends, down to the second: a window that ended an hour ago holds none of today's test changes.
    await screen.getByRole("button", "Last 15 min").tap();
    const span = screen.getByRole("dialog", "Time span");
    await span.getByLabel("From").fill(manila(-7200));
    await span.getByLabel("To").fill(manila(-3600 * 1.5));
    await span.getByRole("button", "Apply").tap();
    await expect(span).toBeHidden();
    await expect(screen.getByRole("button", / – /)).toBeVisible();

    await screen.getByRole("button", "Clear").tap();
    await expect(screen.getByRole("button", "Any time")).toBeVisible();
});
