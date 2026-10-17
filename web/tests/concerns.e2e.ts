import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Concerns: type one in; a new name joins the shared list, a known one is picked from matches.
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
    await dialog.getByLabel(/^Other concerns/).fill("stiff since Monday");
    await dialog.getByRole("button", "Add session").tap();

    await screen.getByText(`${first} Tester`).first().tap();
    const box = screen.getByLabel("Add concern");
    await box.fill(concern);
    await screen.getByText(`Add “${concern}” as a new concern`).tap();
    await expect(screen.getByRole("row").filter({ hasText: concern })).toBeVisible();
    await expect(screen.getByText("stiff since Monday", { exact: false })).toBeVisible();

    // Remove it, then find it again from the list by part of its name.
    await screen.getByRole("row").filter({ hasText: concern }).getByRole("button", "Remove").tap();
    await expect(screen.getByText("None recorded.")).toBeVisible();
    await box.fill(first);
    await expect(screen.getByText(`Add “${first}” as a new concern`)).toBeVisible();
    await screen.getByRole("row").filter({ hasText: concern }).getByText("Add").tap();
    await expect(screen.getByRole("row").filter({ hasText: concern }).getByRole("button", "Remove")).toBeVisible();
});
