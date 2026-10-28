import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("change my name and email, then put them back", { session: "owner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    await app.open("/#/profile");
    await expect(screen.getByRole("heading", "My profile")).toBeVisible();
    const name = screen.getByLabel(/^Name/);
    const email = screen.getByLabel(/^Email/);
    await expect(name).toHaveValue("Dev owner");
    await expect(email).toHaveValue("owner@example.com");

    await name.fill(`Dev owner ${tag}`);
    await email.fill(`owner${tag}@example.com`);
    await screen.getByRole("button", "Save").tap();
    // Save is also disabled while the request is in flight; the banner changes only once it lands.
    await expect(screen.getByRole("link", `Dev owner ${tag}`)).toBeVisible();

    await app.open("/#/today");
    await app.open("/#/profile");
    await expect(screen.getByLabel(/^Name/)).toHaveValue(`Dev owner ${tag}`);

    await screen.getByLabel(/^Name/).fill("Dev owner");
    await screen.getByLabel(/^Email/).fill("owner@example.com");
    await screen.getByRole("button", "Save").tap();
    await expect(screen.getByLabel(/^Email/)).toHaveValue("owner@example.com");
    await expect(screen.getByRole("button", "Save")).toBeDisabled();
});

test("a wrong current password is refused", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/profile");
    await screen.getByLabel("Current password").fill("not-my-password");
    await screen.getByLabel(/^New password/).fill("whatever-123");
    await screen.getByLabel("Repeat new password").fill("whatever-123");
    await screen.getByRole("button", "Change password").tap();
    await expect(screen.getByText("Current password is wrong")).toBeVisible();
});
