import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Developer "View as" (feedback 378fdb6d). Uses the local-only `dev` superuser from the dev seed.
test("a developer views the app as a staff member, look-only, then goes back", async ({ app, screen }) => {
    await app.open("/");
    await screen.getByLabel("Username").fill("dev");
    await screen.getByLabel("Password").fill("dev-dev-pass");
    await screen.getByRole("button", "Sign in").tap();
    const nav = screen.getByRole("navigation", "Main");
    await expect(nav).toBeVisible();

    await nav.getByRole("link", "Users").tap();
    await expect(screen.getByRole("button", "Invite user")).toBeVisible();
    await screen.getByRole("row").filter({ hasText: "Dev staff" }).getByRole("button", "View as").tap();
    const bar = screen.getByRole("status").filter({ hasText: "Viewing as" });
    await expect(bar).toBeVisible();
    // Staff can't manage people, so their menu has no Users page.
    await expect(nav.getByRole("link", "Users")).toBeHidden();

    // Look-only: a change is refused until "Allow changes" is on.
    await screen.getByRole("navigation", "Main").getByRole("link", "My profile").tap();
    const name = screen.getByLabel(/^Name/);
    const before = await name.inputValue();
    await name.fill(`${before} x`);
    await screen.getByRole("button", "Save").tap();
    await expect(screen.getByText(/Look-only/)).toBeVisible();

    await bar.getByRole("button", "Back to me").tap();
    await expect(bar).toBeHidden();
    await expect(nav.getByRole("link", "Users")).toBeVisible();
});
