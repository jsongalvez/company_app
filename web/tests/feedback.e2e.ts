import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("rename a branch", { session: "owner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    const branch = `Rename Me ${tag}`;
    const renamed = `Renamed ${tag}`;

    await app.open("/#/branches");
    await screen.getByRole("button", "New branch").tap();
    const nb = screen.getByRole("dialog", "New branch");
    await nb.getByLabel(/^Name/).fill(branch);
    await nb.getByRole("button", "Create").tap();
    await expect(nb).toBeHidden();
    await screen.getByRole("row").filter({ hasText: branch }).tap();
    await screen.getByRole("button", "Rename branch").tap();
    const rn = screen.getByRole("dialog", `Rename ${branch}`);
    await rn.getByLabel(/^New name/).fill(renamed);
    await rn.getByRole("button", "Rename").tap();
    await expect(rn).toBeHidden();
    await expect(screen.getByText(`Staff at ${renamed}`)).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: renamed })).toBeVisible();
});

test("send a problem report and mark it done in the inbox", { session: "owner" }, async ({ app, screen }) => {
    const message = `The total looks wrong ${Date.now()}`;

    await app.open("/#/today");
    await screen.getByRole("button", "Feedback").tap();
    const fb = screen.getByRole("dialog", "Send feedback");
    await fb.getByRole("button", "Something's wrong").tap();
    await fb.getByLabel(/^What happened/).fill(message);
    await fb.getByRole("button", "Send").tap();
    await expect(fb).toBeHidden();

    await app.open("/#/feedback");
    const row = screen.getByRole("row").filter({ hasText: message });
    await expect(row).toBeVisible();
    await expect(row).toContainText("#/today");
    await row.getByRole("button", "Mark done").tap();
    await expect(row).toBeHidden();
    await screen.getByRole("button", "Done").tap();
    await expect(screen.getByRole("row").filter({ hasText: message })).toBeVisible();
});

test("day and night icon switches the theme", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/today");
    const toNight = screen.getByRole("button", "Switch to night mode");
    const toDay = screen.getByRole("button", "Switch to day mode");
    if (await toDay.isVisible()) await toDay.tap();
    await toNight.tap();
    await expect(toDay).toBeVisible();
    await toDay.tap();
    await expect(toNight).toBeVisible();
});
