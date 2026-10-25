import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("tap several names to add them to a branch, then reorder them", { session: "owner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    const names = [`Ana Stafford ${tag}`, `Ben Stafford ${tag}`, `Cy Stafford ${tag}`];
    const branch = `Staff Branch ${tag}`;

    await app.open("/#/users");
    for (const [i, name] of names.entries()) {
        await screen.getByRole("button", "Invite user").tap();
        const invite = screen.getByRole("dialog", "Invite user");
        await invite.getByLabel(/^Full name/).fill(name);
        await invite.getByLabel(/^Username/).fill(`stf${tag}${i}`);
        await invite.getByLabel(/^Email/).fill(`stf${tag}${i}@example.invalid`);
        await invite.getByLabel("Practitioner").check();
        await invite.getByRole("button", "Create invite").tap();
        await expect(invite.getByLabel("Invite code")).toBeVisible();
        await invite.getByRole("button", "Done").tap();
        await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    }

    await app.open("/#/branches");
    await screen.getByRole("button", "New branch").tap();
    const nb = screen.getByRole("dialog", "New branch");
    await nb.getByLabel(/^Name/).fill(branch);
    await nb.getByRole("button", "Create").tap();
    await expect(nb).toBeHidden();
    await screen.getByRole("row").filter({ hasText: branch }).tap();
    await expect(screen.getByText(`Staff at ${branch}`)).toBeVisible();

    // One dialog, three taps, one save. Search narrows the list; picks survive a changed search.
    await screen.getByRole("button", "Add person").tap();
    const add = screen.getByRole("dialog", `Add person to ${branch}`);
    await add.getByLabel("Search people").fill(tag);
    await add.getByRole("row").filter({ hasText: names[2] }).tap();
    await add.getByRole("row").filter({ hasText: names[0] }).tap();
    await add.getByRole("row").filter({ hasText: names[1] }).tap();
    await add.getByRole("row").filter({ hasText: names[1] }).tap(); // untap Ben
    await add.getByRole("button", "Add 2").tap();
    await expect(add).toBeHidden();

    // Slots follow tap order: Cy, then Ana. Ben was untapped.
    const rows = screen.getByRole("main").getByRole("row").filter({ hasText: "Stafford" });
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText(names[2]);
    await expect(rows.nth(1)).toContainText(names[0]);

    // Add Ben afterwards, then move him to the top.
    await screen.getByRole("button", "Add person").tap();
    const again = screen.getByRole("dialog", `Add person to ${branch}`);
    await again.getByLabel("Search people").fill(tag);
    await expect(again.getByRole("row").filter({ hasText: names[0] })).toBeHidden();
    await again.getByRole("row").filter({ hasText: names[1] }).tap();
    await again.getByRole("button", "Add 1").tap();
    await expect(again).toBeHidden();
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(2)).toContainText(names[1]);
    await rows.nth(2).getByRole("button", "Move up").tap();
    // Each move shows before the next tap, as a person sees it; arrows are off while a move is saving.
    await expect(rows.nth(1)).toContainText(names[1]);
    await expect(rows.nth(1).getByRole("button", "Move up")).toBeEnabled();
    await rows.nth(1).getByRole("button", "Move up").tap();
    await expect(rows.nth(0)).toContainText(names[1]);
    await expect(rows.nth(1)).toContainText(names[2]);
    await expect(rows.nth(2)).toContainText(names[0]);
});
