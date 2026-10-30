import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";

// Owed #25(a): one page where the Owner pays at every branch, wages and allowances only. The user: "A non-member
// Owner can pay members for earlier days too, not just today (wages and allowances only; commission stays private)."
// role-owner is a member of Owner Branch only; Practitioner Branch has a day yesterday (tools/seed/roles.sql).
test("an owner pays a member of another branch for yesterday, from one page for every branch", { session: "role-owner" }, async ({ app, screen, browser }) => {
    const amount = String(400 + (Date.now() % 97));
    await app.open("/#/today");
    await screen.getByRole("navigation", "Main").getByRole("link", "Pay, all branches").tap();
    for (const branch of ["Owner Branch", "Practitioner Branch"]) await expect(screen.getByRole("region", branch)).toBeVisible();

    await screen.getByRole("button", "Previous day").tap();
    const branch = screen.getByRole("region", "Practitioner Branch");
    await branch.getByRole("region", "Pay").getByRole("button", "Add").tap();
    const pay = screen.getByRole("dialog", "Add pay");
    await pay.getByLabel(/^Person/).selectOption({ label: "Role Practitioner" });
    await pay.getByLabel(/^Amount/).fill(amount);
    await pay.getByLabel(/^Reason/).fill("paid late");
    await pay.getByRole("button", "Save").tap();
    await expect(pay).toBeHidden();
    await expect(branch.getByRole("region", "Pay").getByRole("row").filter({ hasText: "Role Practitioner" }).filter({ hasText: amount })).toBeVisible();

    await branch.getByRole("region", "Allowances").getByRole("button", "Add").tap();
    const allowance = screen.getByRole("dialog", "Add allowance");
    await allowance.getByLabel(/^Person/).selectOption({ label: "Role Practitioner" });
    await allowance.getByLabel(/^Amount/).fill("75");
    await allowance.getByLabel(/^Reason/).fill("paid late");
    await allowance.getByRole("button", "Save").tap();
    await expect(allowance).toBeHidden();
    await expect(branch.getByRole("region", "Allowances").getByRole("row").filter({ hasText: "Role Practitioner" }).first()).toBeVisible();

    expect(new URL(await browser.url()).hash).toContain(`date=${businessDate(-1)}`);
    await expect(screen.getByText(/commission/i)).toBeHidden();
});

// The user: "with nothing recorded that day, mean no one clocked in that day? If so no need to allow paying."
// Yes: a clock-in makes the day (AttendanceService.clockIn), so a branch with no day is one nobody clocked in at.
test("a branch nobody clocked in at says so, with nothing to pay", { session: "role-owner" }, async ({ app, screen }) => {
    await app.open("/#/pay?date=2025-01-06");
    const branch = screen.getByRole("region", "Practitioner Branch");
    await expect(branch.getByText("No one clocked in.")).toBeVisible();
    await expect(branch.getByRole("button", "Add")).toHaveCount(0);
});
