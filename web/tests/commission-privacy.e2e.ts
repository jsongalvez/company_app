import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";

// Owed #26, the user: "Only members of the same branch can see their commisions, since they would be
// sharing it irl." and "Shouldn't be included in sales reports." Practitioner Branch has a sale
// yesterday (tools/seed/roles.sql); the Owner and the Accountant are not its members.
for (const who of ["owner", "accountant"] as const) {
    test(`${who === "owner" ? "an owner" : "an accountant"} outside the branch sees no commission`, { session: `role-${who}` }, async ({ app, screen }) => {
        await app.open("/#/reports");
        await screen.getByLabel("Branch").selectOption({ label: "Practitioner Branch" });
        await expect(screen.getByRole("columnheader", "Net")).toBeVisible();
        await expect(screen.getByRole("columnheader", "Commission")).toBeHidden();

        await app.open(`/#/finance?date=${businessDate(-1)}`);
        await expect(screen.getByRole("heading", "Expenses")).toBeVisible();
        await expect(screen.getByRole("heading", "Commission share")).toBeHidden();
        await expect(screen.getByText("Commission", { exact: true })).toBeHidden();
    });
}
