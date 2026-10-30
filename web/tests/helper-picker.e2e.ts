import { test } from "@e2e-dev/web";
import { expect, credentials } from "e2e";

type Screen = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["screen"];
type App = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["app"];

async function signIn(app: App, screen: Screen, username: string, password: string) {
    await app.clearState();
    await app.open("/");
    await screen.getByLabel("Username").fill(username);
    await screen.getByLabel("Password").fill(password);
    await screen.getByRole("button", "Sign in").tap();
    await expect(screen.getByRole("navigation", "Main")).toBeAttached();
}

// A mission helper (a Branches-page helper, an active Manager who is not a member) has relief's rights there until
// removed. The user: "A helper shares commissions, just think of it as a relief but indefinite until removed." So the
// mission shows in their branch picker.
test("a mission helper finds the mission in their branch picker", { session: "owner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    const name = `Hal Helper ${tag}`;
    const username = `hlp${tag}`;
    const password = `helper-pass-${tag}`;
    const mission = `Mission ${tag}`;

    await app.open("/#/users");
    await screen.getByRole("button", "Invite user").tap();
    const invite = screen.getByRole("dialog", "Invite user");
    await invite.getByLabel(/^Full name/).fill(name);
    await invite.getByLabel(/^Username/).fill(username);
    await invite.getByLabel(/^Email/).fill(`${username}@example.invalid`);
    await invite.getByLabel("Manager").check();
    await invite.getByRole("button", "Create invite").tap();
    const code = await invite.getByLabel("Invite code").inputValue();
    await invite.getByRole("button", "Done").tap();

    await app.clearState();
    await app.open("/");
    await screen.getByRole("link", "Have an invite code?").tap();
    await screen.getByLabel("Invite code").fill(code);
    await screen.getByLabel("New password").fill(password);
    await screen.getByLabel("Repeat password").fill(password);
    await screen.getByRole("button", "Set password").tap();
    await expect(screen.getByText("Password set. Sign in with it now.")).toBeVisible();

    const owner = credentials.user("owner");
    await signIn(app, screen, owner.username, owner.password);
    await app.open("/#/branches");
    await screen.getByRole("button", "New branch").tap();
    const nb = screen.getByRole("dialog", "New branch");
    await nb.getByLabel(/^Name/).fill(mission);
    await nb.getByLabel("Kind").selectOption({ label: "Medical mission" });
    await nb.getByRole("button", "Create").tap();
    await expect(nb).toBeHidden();
    await screen.getByRole("row").filter({ hasText: mission }).tap();
    await screen.getByRole("button", "Add helper").tap();
    const add = screen.getByRole("dialog", "Add mission helper");
    await add.getByLabel("Search people").fill(tag);
    await add.getByRole("row").filter({ hasText: name }).tap();
    await add.getByRole("button", "Add").tap();
    await expect(add).toBeHidden();

    await signIn(app, screen, username, password);
    await expect(screen.getByLabel("Branch").getByRole("option", mission)).toBeAttached();
});
