import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
import { doneSessionForNewClient } from "./support";

const today = businessDate();

test("send a remittance for today, then undo it", { session: "owner" }, async ({ app, screen, browser }) => {
    const client = await doneSessionForNewClient(app, screen, browser);
    await app.open("/#/remittance");
    await screen.getByRole("button", "New remittance").tap();
    const d = screen.getByRole("dialog", "New remittance");
    await d.getByLabel(/^From/).fill(today);
    await d.getByLabel(/^To/).fill(today);
    await d.getByRole("button", "Create draft").tap();

    await screen.getByRole("button", "Add everything in range").tap();
    await expect(screen.getByRole("row").filter({ hasText: client })).toBeVisible();
    await screen.getByRole("button", "Send to office").tap();
    await screen.getByRole("dialog", "Send remittance").getByRole("button", "Send to office").tap();
    await expect(screen.getByText("Sent", { exact: true }).first()).toBeVisible();

    await screen.getByRole("button", "Undo").tap();
    const u = screen.getByRole("dialog", "Undo remittance");
    await u.getByLabel(/^Reason/).fill("e2e test");
    await u.getByRole("button", "Return to draft").tap();
    await expect(screen.getByText("Draft", { exact: true }).first()).toBeVisible();
});
