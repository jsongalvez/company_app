import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Feedback 503bf228: a branch member sets the branch's own list price per product and one
// commission for all its products; sales there charge them, and "Use shared" goes back.
test("a branch sets its own product price and sells at it", { session: "owner" }, async ({ app, screen, browser }) => {
    const tag = Date.now().toString().slice(-6);
    const category = `Balms ${tag}`;
    const product = `Test balm ${tag}`;

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
    await expect(screen.getByRole("row").filter({ hasText: product })).toBeVisible();

    await app.open("/#/inventory");
    await clockInHere(screen, browser);
    await screen.getByRole("button", "Add product").tap();
    const add = screen.getByRole("dialog", "Add product to this branch");
    await add.getByLabel(/^Product/).selectOption({ label: `${product} · 250.00` });
    await add.getByRole("button", "Add").tap();
    const card = screen.getByRole("row").filter({ hasText: product }).filter({ hasText: "Fix count" });
    await card.getByRole("button", "Restock").tap();
    const restock = screen.getByRole("dialog", `Restock: ${product}`);
    await restock.getByLabel(/^Quantity/).fill("5");
    await restock.getByRole("button", "Restock").tap();
    await expect(restock).toBeHidden();

    // Branches page: this branch's price 300, and commission 40 on every product.
    await app.open("/#/branches");
    await screen.getByRole("row").filter({ hasText: "Fixture Branch" }).first().tap();
    const priced = screen.getByRole("row").filter({ hasText: product });
    await priced.getByRole("button", "Change").tap();
    const dlg = screen.getByRole("dialog", new RegExp(`Price at .*: ${product}`));
    await dlg.getByLabel(/^List price/).fill("300");
    await dlg.getByRole("button", "Save").tap();
    await expect(dlg).toBeHidden();
    await expect(priced.getByText("this branch").first()).toBeVisible();
    await screen.getByRole("button", "Change commission").tap();
    const com = screen.getByRole("dialog", /^Commission at /);
    await com.getByLabel(/^Commission on every product/).fill("40");
    await com.getByRole("button", "Save").tap();
    await expect(com).toBeHidden();
    await expect(priced.getByText(/^40\.00/)).toBeVisible();

    // A sale here charges the branch price.
    await app.open("/#/inventory");
    await screen.getByRole("button", "Product sale").tap();
    const sale = screen.getByRole("dialog", "Product sale");
    await sale.getByLabel(/^Product/).selectOption({ label: `${product} · 300.00 · 5 left` });
    await sale.getByLabel(/^Quantity/).fill("2");
    await expect(sale.getByLabel(/^Total/)).toHaveValue("600.00");
    await sale.getByRole("button", "Close").tap();

    // Back to the shared price.
    await app.open("/#/branches");
    await screen.getByRole("row").filter({ hasText: "Fixture Branch" }).first().tap();
    await priced.getByRole("button", "Change").tap();
    await dlg.getByRole("button", "Use shared price").tap();
    await expect(dlg).toBeHidden();
    await screen.getByRole("button", "Change commission").tap();
    await com.getByRole("button", "Use shared commissions").tap();
    await expect(com).toBeHidden();
    await expect(priced.getByText("this branch")).toBeHidden();
    await expect(priced.getByText(/^25\.00/)).toBeVisible();
});
