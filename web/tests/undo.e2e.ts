import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

test("undo a session marked done by mistake", { session: "owner" }, async ({ app, screen }) => {
    const name = await doneSessionForNewClient(app, screen);
    const row = screen.getByRole("row").filter({ hasText: name });
    await row.getByRole("button", "Undo").tap();
    await expect(row.getByText("Waiting")).toBeVisible();
    await expect(row.getByRole("button", "Done")).toBeVisible();
});
