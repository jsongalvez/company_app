import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("invite with just a code, the invitee names themselves", { session: "owner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    const name = `Code Only ${tag}`;
    const username = `code${tag}`;
    const password = `code-pass-${tag}`;

    await app.open("/#/users");
    await screen.getByRole("button", "Invite user").tap();
    const invite = screen.getByRole("dialog", "Invite user");
    await invite.getByRole("button", "Create invite").tap();
    const codeBox = invite.getByLabel("Invite code");
    await expect(codeBox).toBeVisible();
    const code = await codeBox.inputValue();
    expect(code.length).toBeGreaterThan(5);
    await invite.getByRole("button", "Done").tap();

    await app.clearState();
    await app.open("/");
    await screen.getByRole("link", "Have an invite code?").tap();
    await screen.getByLabel("Invite code").fill(code);
    await screen.getByLabel("New password").fill(password);
    await screen.getByLabel("Repeat password").fill(password);
    await screen.getByRole("button", "Set password").tap();
    await expect(screen.getByText("Username is required")).toBeVisible();

    await screen.getByLabel("Full name").fill(name);
    await screen.getByLabel("Username").fill(username);
    await screen.getByLabel("Email").fill(`${username}@example.invalid`);
    await screen.getByRole("button", "Set password").tap();
    await expect(screen.getByText("Password set. Sign in with it now.")).toBeVisible();

    await screen.getByLabel("Username").fill(username);
    await screen.getByLabel("Password").fill(password);
    await screen.getByRole("button", "Sign in").tap();
    await expect(screen.getByRole("navigation", "Main")).toBeVisible();
});
