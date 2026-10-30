import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback a11802ff: Branches "should be visible to branch members but they can only see and edit their branch
// settings." role-practitioner belongs to Practitioner Branch only (tools/seed/roles.sql).
test("a member opens Branches and sees only their own branch, its staff and prices", { session: "role-practitioner" }, async ({ app, screen }) => {
    await app.open("/#/today");
    await screen.getByRole("navigation", "Main").getByRole("link", "Branches").tap();
    await expect(screen.getByText("Staff at Practitioner Branch")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Role Practitioner" })).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Owner Branch" })).toHaveCount(0);
    await expect(screen.getByRole("heading", "Price list")).toBeVisible();
    await expect(screen.getByRole("button", "Change").first()).toBeVisible();
    await expect(screen.getByRole("button", "Rename branch")).toHaveCount(0);
    await expect(screen.getByRole("button", "New branch")).toHaveCount(0);
});
