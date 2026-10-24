import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";

test("invite a practitioner, they sign in, add them to a new branch", { session: "owner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    const name = `Pat Tester ${tag}`;
    const username = `pat${tag}`;
    const password = `pat-pass-${tag}`;
    const branch = `Test Branch ${tag}`;

    // Owner invites them.
    await app.open("/#/users");
    await screen.getByRole("button", "Invite user").tap();
    const invite = screen.getByRole("dialog", "Invite user");
    await invite.getByLabel(/^Full name/).fill(name);
    await invite.getByLabel(/^Username/).fill(username);
    await invite.getByLabel(/^Email/).fill(`${username}@example.invalid`);
    await invite.getByLabel("Practitioner").check();
    await invite.getByRole("button", "Create invite").tap();
    const codeBox = invite.getByLabel("Invite code");
    await expect(codeBox).toBeVisible();
    const code = await codeBox.inputValue();
    expect(code.length).toBeGreaterThan(5);
    await invite.getByRole("button", "Done").tap();
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();

    // Owner makes a branch and puts them on its staff.
    await app.open("/#/branches");
    await screen.getByRole("button", "New branch").tap();
    const nb = screen.getByRole("dialog", "New branch");
    await nb.getByLabel(/^Name/).fill(branch);
    await nb.getByRole("button", "Create").tap();
    await expect(nb).toBeHidden();
    await screen.getByRole("row").filter({ hasText: branch }).tap();
    await expect(screen.getByText(`Staff at ${branch}`)).toBeVisible();
    await screen.getByRole("button", "Add person").tap();
    const add = screen.getByRole("dialog", `Add person to ${branch}`);
    // Type to find them instead of scrolling a long list.
    await add.getByLabel("Search people").fill(tag);
    await add.getByRole("row").filter({ hasText: name }).tap();
    await expect(add.getByText(username)).toBeVisible();
    await add.getByRole("button", "Add").tap();
    await expect(add).toBeHidden();
    await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();

    // They use the code, then sign in and land on their branch.
    // Forget the owner's login in this browser only. Signing out would end it on
    // the server too, and other tests share that login.
    await app.clearState();
    await app.open("/");
    await expect(screen.getByRole("button", "Sign in")).toBeVisible();
    await screen.getByRole("link", "Have an invite code?").tap();
    await screen.getByLabel("Invite code").fill(code);
    await screen.getByLabel("New password").fill(password);
    await screen.getByLabel("Repeat password").fill(password);
    await screen.getByRole("button", "Set password").tap();
    await expect(screen.getByText("Password set. Sign in with it now.")).toBeVisible();
    await screen.getByLabel("Username").fill(username);
    await screen.getByLabel("Password").fill(password);
    await screen.getByRole("button", "Sign in").tap();
    await expect(screen.getByRole("navigation", "Main")).toBeVisible();
    await expect(screen.getByLabel("Branch")).toHaveValue(/.+/);
    await expect(screen.getByLabel("Branch")).toContainText(branch);
    // Practitioners don't send remittances or see pay.
    await expect(screen.getByRole("navigation", "Main").getByRole("link", "Remittance")).toBeHidden();
    for (const page of ["Clients", "Stock", "Expenses & pay", "Sales reports", "Relief duty", "Change history"]) {
        await screen.getByRole("navigation", "Main").getByRole("link", page).tap();
        await expect(screen.getByRole("main")).toBeVisible();
        await new Promise((r) => setTimeout(r, 700));
    }
    await expect(screen.getByRole("heading", "Pay")).toBeHidden();

    // They ask to work a shift at the owner's branch a few days out.
    const iso = businessDate(5);
    await app.open("/#/relief");
    await screen.getByRole("button", "Ask for relief").tap();
    const ask = screen.getByRole("dialog", "Ask for relief");
    await ask.getByLabel(/^Branch/).selectOption({ label: "K6 Fixture Branch" });
    await ask.getByLabel(/^Day/).fill(iso);
    await ask.getByRole("button", "Send request").tap();
    await expect(ask).toBeHidden();

    // The owner sees it on the relief page without picking that day.
    await app.clearState();
    await app.open("/");
    await screen.getByLabel("Username").fill("owner");
    await screen.getByLabel("Password").fill(process.env.E2E_OWNER_PASSWORD ?? "owner-dev-pass");
    await screen.getByRole("button", "Sign in").tap();
    await expect(screen.getByRole("navigation", "Main")).toBeVisible();
    await screen.getByLabel("Branch").selectOption({ label: "K6 Fixture Branch" });
    await app.open("/#/relief");
    const req = screen.getByRole("row").filter({ hasText: name });
    await expect(req).toBeVisible();
    await expect(req.getByRole("button", "Approve")).toBeVisible();
});
