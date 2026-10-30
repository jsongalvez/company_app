import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
import { doneSessionForNewClient } from "./support";

const today = businessDate();

test("send a remittance for today, then undo it", { session: "owner" }, async ({ app, screen, browser }) => {
    const client = await doneSessionForNewClient(app, screen, browser);
    // A slow list of the range's sessions: "Add everything" must wait for it, not add nothing.
    await browser.route(/\/remittance-sessions/, async (route) => {
        await new Promise((done) => setTimeout(done, 1500));
        await route.fallback();
    });
    await app.open("/#/remittance");
    await screen.getByRole("button", "New remittance").tap();
    const d = screen.getByRole("dialog", "New remittance");
    await d.getByLabel(/^From/).fill(today);
    await d.getByLabel(/^To/).fill(today);
    await d.getByRole("button", "Create draft").tap();

    await screen.getByRole("button", "Add all").tap();
    await expect(screen.getByRole("checkbox", client)).toBeChecked();
    await screen.getByRole("button", "Send to office").tap();
    await screen.getByRole("dialog", "Send remittance").getByRole("button", "Send to office").tap();
    await expect(screen.getByText("Sent", { exact: true }).first()).toBeVisible();

    await screen.getByRole("button", "Undo").tap();
    const u = screen.getByRole("dialog", "Undo remittance");
    await u.getByLabel(/^Reason/).fill("e2e test");
    await u.getByRole("button", "Return to draft").tap();
    await expect(screen.getByText("Draft", { exact: true }).first()).toBeVisible();
});

// Feedback ec49b5dd: "Remittance ix needs work, i don't understand what to do." The draft is two numbered
// checklists, days then sessions; ticked means included. 364b18b3: its kind can't change once items are in.
test("a draft remittance is two checklists, and its kind stays once something is in", { session: "owner" }, async ({ app, screen, browser }) => {
    const client = await doneSessionForNewClient(app, screen, browser);
    await app.open("/#/remittance");
    await screen.getByRole("button", "New remittance").tap();
    const d = screen.getByRole("dialog", "New remittance");
    await d.getByLabel(/^From/).fill(today);
    await d.getByLabel(/^To/).fill(today);
    await d.getByRole("button", "Create draft").tap();

    await expect(screen.getByRole("heading", "1 · Days")).toBeVisible();
    await expect(screen.getByRole("heading", "2 · Sessions")).toBeVisible();
    await expect(screen.getByRole("button", "Send to office")).toBeDisabled();

    await screen.getByRole("row").filter({ hasText: client }).tap();
    await expect(screen.getByRole("checkbox", client)).toBeChecked();
    await screen.getByRole("row").filter({ hasText: client }).tap();
    await expect(screen.getByRole("checkbox", client)).not.toBeChecked();
    await screen.getByRole("row").filter({ hasText: client }).tap();
    await expect(screen.getByRole("checkbox", client)).toBeChecked();

    await screen.getByRole("button", "Edit").tap();
    await expect(screen.getByRole("dialog", "Edit remittance").getByLabel("Kind")).toBeDisabled();
});
