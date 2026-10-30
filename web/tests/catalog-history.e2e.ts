import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Owed #25(c), the user: "Practitioners, Coordinators, Managers, and Owner can manage the catalog. Just have
// the change history easy to view." One tap on a product shows who changed what, from what to what.
test("a practitioner changes a product and opens its history in one tap", { session: "role-practitioner" }, async ({ app, screen }) => {
    const tag = Date.now().toString().slice(-6);
    const category = `History oils ${tag}`;
    const product = `History oil ${tag}`;

    await app.open("/#/catalog");
    await screen.getByRole("button", "Add category").tap();
    const cat = screen.getByRole("dialog", "Add category");
    await cat.getByLabel(/^Name/).fill(category);
    await cat.getByRole("button", "Add").tap();
    await expect(cat).toBeHidden();

    await screen.getByRole("button", "Add product").tap();
    const form = screen.getByRole("dialog", "Add product");
    await form.getByLabel(/^Name/).fill(product);
    await form.getByLabel(/^Category/).selectOption({ label: category });
    await form.getByLabel(/^Price/).fill("250");
    await form.getByLabel(/^Commission per unit/).fill("25");
    await form.getByRole("button", "Save").tap();
    const row = screen.getByRole("row").filter({ hasText: product });
    await row.getByRole("button", "Edit").tap();
    const edit = screen.getByRole("dialog", `Edit ${product}`);
    await edit.getByLabel(/^Price/).fill("300");
    await edit.getByRole("button", "Save").tap();
    await expect(edit).toBeHidden();

    await row.getByRole("button", "History").tap();
    const change = screen.getByRole("row").filter({ hasText: /250(\.00)? → 300/ }).last();
    await expect(change).toBeVisible();
    await expect(change).toContainText("Role Practitioner");

    await screen.getByRole("link", "Category changes").tap();
    await expect(screen.getByRole("row").filter({ hasText: category })).toBeVisible();
});
