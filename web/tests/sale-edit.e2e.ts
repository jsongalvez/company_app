import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
type Screen = Parameters<Parameters<typeof test>[2]>[0]["screen"];

async function giveReason(screen: Screen, reason: string) {
    const ask = screen.getByRole("dialog", "Reason for change");
    await expect(ask).toBeVisible();
    await ask.getByLabel("Reason").fill(reason);
    await ask.getByRole("button", "Save").tap();
    await expect(ask).toHaveCount(0);
}

// Owed #21: "wait, you cant edit product sales?" A session's page lists its sales, and a member
// changes or deletes one on an earlier day with a reason; its history names who, what and why.
test("a practitioner changes and deletes yesterday's sale from its session page", { session: "role-practitioner" }, async ({ app, screen }) => {
    await app.open(`/#/today?date=${businessDate(-1)}`);
    await screen.getByRole("row").filter({ hasText: "Sale Dayclient" }).first().tap();
    await expect(screen.getByRole("heading", "Product sales")).toBeVisible();
    // A sale added from the session's page goes to the session's own day and shows in its list at once.
    await screen.getByRole("button", "Product sale").tap();
    const add = screen.getByRole("dialog", "Product sale");
    await add.getByLabel(/^Product/).selectOption({ index: 1 });
    await add.getByRole("button", "Record sale").tap();
    await expect(add).toHaveCount(0);
    await expect(screen.getByRole("row").filter({ hasText: "Role test oil" })).toHaveCount(2);
    const sale = screen.getByRole("row").filter({ hasText: "Role test oil" }).first();
    await expect(sale).toContainText("100.00");

    await sale.getByRole("button", "Change sale").tap();
    const change = screen.getByRole("dialog", "Change sale");
    await change.getByLabel(/^Quantity/).fill("2");
    await change.getByRole("button", "Save").tap();
    await giveReason(screen, "Counted two bottles");
    await expect(sale).toContainText("200.00");

    await sale.getByRole("button", "Sale history").tap();
    const latest = screen.getByRole("row").filter({ hasText: "Counted two bottles" }).last();
    await expect(latest).toContainText("Role Practitioner");

    await sale.getByRole("button", "Delete sale").tap();
    await giveReason(screen, "Returned unopened");
    await expect(screen.getByRole("row").filter({ hasText: "Role test oil" })).toHaveCount(1);
});

// "4 yes": an earlier day with nothing recorded yet takes an expense.
test("a practitioner adds an expense to an earlier day with nothing recorded", { session: "role-practitioner" }, async ({ app, screen }) => {
    const date = businessDate(-(40 + Math.floor(Math.random() * 300)));
    const note = `Gas${Date.now().toString().slice(-6)}`;
    await app.open(`/#/finance?date=${date}`);
    await expect(screen.getByText(/Nothing was recorded/)).toBeVisible();
    await screen.getByRole("button", "Add expense").tap();
    const add = screen.getByRole("dialog", "Add expense");
    await add.getByLabel(/^Amount/).fill("75");
    await add.getByLabel(/^Notes/).fill(note);
    await add.getByRole("button", "Save").tap();
    await expect(screen.getByRole("row").filter({ hasText: note })).toBeVisible();
});

// A day's sales are listed on Expenses & pay, so a walk-in sale with no session is changed there too.
test("Today's product sales open the day's list, where a walk-in sale can be changed", { session: "role-practitioner" }, async ({ app, screen }) => {
    await app.open("/#/today");
    await screen.getByRole("button", "Product sale").tap();
    const add = screen.getByRole("dialog", "Product sale");
    await add.getByLabel(/^Product/).selectOption({ index: 1 });
    await add.getByRole("button", "Record sale").tap();
    await expect(add).toHaveCount(0);

    await screen.getByRole("link", "Product sales").tap();
    await expect(screen.getByRole("heading", "Product sales")).toBeVisible();
    const sale = screen.getByRole("row").filter({ hasText: "Role test oil" }).last();
    await sale.getByRole("button", "Change sale").tap();
    await expect(screen.getByRole("dialog", "Change sale")).toBeVisible();
});
