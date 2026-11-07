import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Feedback 503bf228, a6946922: at a branch every product has its own list price and its own
// commission. Changing one product leaves the others alone, and a sale charges the new price.
test("a branch sets the price and commission of one product and sells at them", { session: "owner" }, async ({ app, screen, browser }) => {
    const tag = Date.now().toString().slice(-6);
    const category = `Balms ${tag}`;
    const changed = `Test balm ${tag}`;
    const untouched = `Test salve ${tag}`;

    await app.open("/#/catalog");
    await screen.getByRole("button", "Add category").tap();
    const cat = screen.getByRole("dialog", "Add category");
    await cat.getByLabel(/^Name/).fill(category);
    await cat.getByRole("button", "Add").tap();
    await expect(cat).toBeHidden();
    for (const name of [changed, untouched]) {
        await screen.getByRole("button", "Add product").tap();
        const form = screen.getByRole("dialog", "Add product");
        await form.getByLabel(/^Name/).fill(name);
        await form.getByLabel(/^Category/).selectOption({ label: category });
        await form.getByLabel(/^Price/).fill("250");
        await form.getByLabel(/^Commission per unit/).fill("25");
        await form.getByRole("button", "Save").tap();
        await expect(screen.getByRole("row").filter({ hasText: name })).toBeVisible();
    }

    await app.open("/#/inventory");
    await clockInHere(screen, browser);
    for (const name of [changed, untouched]) {
        await screen.getByRole("button", "Add product").tap();
        const add = screen.getByRole("dialog", "Add product to this branch");
        await add.getByLabel(/^Product/).selectOption({ label: `${name} · 250.00` });
        await add.getByRole("button", "Add").tap();
        const card = screen.getByRole("row").filter({ hasText: name }).filter({ hasText: "Fix count" });
        await card.getByRole("button", "Restock").tap();
        const restock = screen.getByRole("dialog", `Restock: ${name}`);
        await restock.getByLabel(/^Quantity/).fill("5");
        await restock.getByRole("button", "Restock").tap();
        await expect(restock).toBeHidden();
    }

    // Stock page (Branches is for people who manage users; coordinators and practitioners cannot open it).
    const row = screen.getByRole("row").filter({ hasText: changed });
    await row.getByRole("button", "Change prices").tap();
    const dlg = screen.getByRole("dialog", new RegExp(`Prices at this branch: ${changed}`));
    await dlg.getByLabel(/^List price/).fill("300");
    await dlg.getByLabel(/^Commission/).fill("40");
    await dlg.getByRole("button", "Save").tap();
    await expect(dlg).toBeHidden();
    await expect(row.getByText(/^300\.00/)).toBeVisible();
    await expect(row.getByText(/^40\.00/)).toBeVisible();

    // The other product keeps its own values, and nothing on the page talks about a shared price.
    const other = screen.getByRole("row").filter({ hasText: untouched });
    await expect(other.getByText(/^250\.00/)).toBeVisible();
    await expect(other.getByText(/^25\.00/)).toBeVisible();
    await expect(screen.getByText(/shared/i)).toBeHidden();
    await expect(screen.getByRole("button", "Change commission")).toBeHidden();

    // A sale here charges the branch price.
    await screen.getByRole("button", "Product sale").tap();
    const sale = screen.getByRole("dialog", "Product sale");
    await sale.getByLabel(/^Product/).selectOption({ label: `${changed} · 300.00 · 5 left` });
    await sale.getByLabel(/^Quantity/).fill("2");
    await expect(sale.getByLabel(/^Total/)).toHaveValue("600.00");
    await sale.getByRole("button", "Close").tap();
});
