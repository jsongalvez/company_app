import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere, doneSessionForNewClient } from "./support";

test("make a product, stock it, restock it and sell some", { session: "owner" }, async ({ app, screen, browser }) => {
    const tag = Date.now().toString().slice(-6);
    const category = `Oils ${tag}`;
    const product = `Test oil ${tag}`;

    // Products page: new category, new product in it.
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

    // Stock page: carry it at this branch, restock 10.
    await app.open("/#/inventory");
    await clockInHere(screen);
    await screen.getByRole("button", "Add product").tap();
    const add = screen.getByRole("dialog", "Add product to this branch");
    await add.getByLabel(/^Product/).selectOption({ label: `${product} · 250.00` });
    await add.getByRole("button", "Add").tap();
    const row = screen.getByRole("row").filter({ hasText: product });
    await expect(row).toBeVisible();

    await row.getByRole("button", "Restock").tap();
    const restock = screen.getByRole("dialog", `Restock: ${product}`);
    await restock.getByLabel(/^Quantity/).fill("10");
    await restock.getByRole("button", "Restock").tap();
    await expect(restock).toBeHidden();
    await expect(row.getByText("10", { exact: true })).toBeVisible();

    // Sell 2 as a walk-in sale: 8 left, 2 sold.
    await screen.getByRole("button", "Product sale").tap();
    const sale = screen.getByRole("dialog", "Product sale");
    await sale.getByLabel(/^Product/).selectOption({ label: `${product} · 250.00 · 10 left` });
    await sale.getByLabel(/^Quantity/).fill("2");
    await expect(sale.getByLabel(/^Total/)).toHaveValue("500.00");
    await sale.getByRole("button", "Record sale").tap();
    await expect(sale).toBeHidden();
    await expect(row.getByText("8", { exact: true })).toBeVisible();
    await expect(row.getByText("2", { exact: true })).toBeVisible();

    // No signal: one sale tied to a session, one walk-in. Both go out once the signal is back.
    const client = await doneSessionForNewClient(app, screen);
    await app.open("/#/inventory");
    const card = screen.getByRole("row").filter({ hasText: product }).filter({ hasText: "Fix count" });
    await expect(card).toBeVisible();
    const down = /\/(api\/|health)/;
    await browser.route(down, (route) => route.abort());
    for (const during of [client, null]) {
        await screen.getByRole("button", "Product sale").tap();
        await sale.getByLabel(/^Product/).selectOption({ label: `${product} · 250.00 · 8 left` });
        await sale.getByLabel(/^Quantity/).fill("1");
        if (during) await sale.getByLabel(/^Sold during session/).selectOption({ label: during });
        await sale.getByRole("button", "Record sale").tap();
        await expect(sale).toBeHidden({ timeout: 20_000 });
    }
    await expect(screen.getByText("2 changes waiting to send", { exact: false })).toBeVisible();
    await browser.unroute(down);
    await expect(screen.getByText("waiting to send", { exact: false })).not.toBeVisible({ timeout: 20_000 });
    await expect(screen.getByText("Couldn't be sent", { exact: false })).not.toBeVisible();
    await app.open("/#/inventory");
    await expect(card.getByText("6", { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(card.getByText("4", { exact: true })).toBeVisible();
});
