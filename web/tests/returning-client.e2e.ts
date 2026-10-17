import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// A client who came before the app: their earlier visits set the visit type and starting price.
test("earlier visits set a returning client's visit type", { session: "owner" }, async ({ app, screen }) => {
    const first = `Back${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await clockInHere(screen);

    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", "New session");
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Returning");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "M" });
    await form.getByLabel(/^Age/).fill("55");
    await form.getByLabel(/^Visits before the app/).fill("1");
    await form.getByRole("button", "Create client").tap();

    await expect(dialog.getByLabel(/^Session type/)).toHaveValue("Second visit");
    await expect(dialog.getByLabel(/^Price/)).toHaveValue("2000.00");
});
