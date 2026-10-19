import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Concerns: pick or type them in the New session popup; a new name joins the shared list.
test("add concerns to a session by typing", { session: "owner" }, async ({ app, screen }) => {
    const first = `Ache${Date.now().toString().slice(-6)}`;
    const concern = `Neck pain ${first}`;
    await app.open("/#/today");
    await clockInHere(screen);

    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", "New session");
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "F" });
    await form.getByLabel(/^Age/).fill("33");
    await form.getByRole("button", "Create client").tap();
    await expect(dialog.getByText(`${first} Tester`, { exact: false })).toBeVisible();
    // In the popup: tick a common concern and type a new one.
    await dialog.getByLabel("Vertigo").check();
    await dialog.getByLabel(/^Other illness/).fill(concern);
    await dialog.getByRole("button", "Add session").tap();

    await screen.getByText(`${first} Tester`).first().tap();
    const box = screen.getByLabel("Add illness");
    await expect(
        screen
            .getByRole("row")
            .filter({ hasText: "Vertigo" })
            .getByRole("button", /^Remove/),
    ).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: concern })).toBeVisible();

    // Remove it, then find it again from the list by part of its name.
    await screen
        .getByRole("row")
        .filter({ hasText: concern })
        .getByRole("button", /^Remove/)
        .tap();
    await box.fill(first);
    await expect(screen.getByText(`Add “${first}” as a new illness`)).toBeVisible();
    await screen.getByRole("row").filter({ hasText: concern }).getByText("Add").tap();
    await expect(
        screen
            .getByRole("row")
            .filter({ hasText: concern })
            .getByRole("button", /^Remove/),
    ).toBeVisible();
});
