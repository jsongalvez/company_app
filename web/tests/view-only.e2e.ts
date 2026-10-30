import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
import { noteRefusals, refused } from "./support";

type Browser = Parameters<Parameters<typeof test>[1]>[0]["browser"];

// Owed #23, the user: the accountant and an owner "can view any branch and see any data a branch member
// sees, view only", and "relief has same access as branch members only for that granted day". The
// Practitioner Branch (tools/seed/roles.sql) has none of them as members; role-relief is granted today.
const editWords = /^(Add|Edit|Delete|Void|Restore|Undo|Product sale|New session|Book|Restock|Recount|Change|Update shares|Done|No-show|Save|Record)/;

/** Enabled buttons on the page that change something (a filter toggle doesn't). The Owner pays at every branch (owed #25), so pay and allowances don't count. */
async function editButtons(browser: Browser): Promise<string[]> {
    const shown = await browser.evaluate(() =>
        [...document.querySelectorAll<HTMLButtonElement>("main button")]
            .filter((b) => b.offsetParent !== null && !b.disabled && !b.hasAttribute("aria-pressed"))
            .filter((b) => !["Pay", "Allowances"].includes(b.closest("section")?.querySelector("h2")?.textContent?.trim() ?? ""))
            .map((b) => b.textContent?.trim() ?? ""),
    );
    return shown.filter((t) => editWords.test(t));
}

for (const who of ["accountant", "owner"] as const) {
    test(
        `${who === "owner" ? "an owner" : "an accountant"} outside the branch sees every page and changes nothing`,
        { session: `role-${who}` },
        async ({ app, screen, browser }) => {
            await noteRefusals(browser);
            await app.open("/#/today");
            await screen.getByLabel("Branch").selectOption({ label: "Practitioner Branch" });
            const nav = screen.getByRole("navigation", "Main");
            for (const page of ["Sessions", "Stock", "Expenses & pay", "Sales reports"]) await expect(nav.getByRole("link", page)).toBeVisible();

            for (const route of [
                "/#/today",
                `/#/today?date=${businessDate(-1)}`,
                "/#/inventory",
                "/#/finance",
                `/#/finance?date=${businessDate(-1)}`,
                "/#/reports",
            ]) {
                await app.open(route);
                await expect(screen.getByRole("main")).toBeVisible();
                await new Promise((done) => setTimeout(done, 700));
                expect({ route, buttons: await editButtons(browser) }).toEqual({ route, buttons: [] });
            }
            await app.open("/#/finance");
            await expect(screen.getByRole("row").filter({ hasText: "Role test oil" }).first()).toBeVisible();
            expect(await refused(browser)).toEqual([]);
        },
    );
}

test("relief sees its day's sales list, and no branch-wide pages", { session: "role-relief" }, async ({ app, screen, browser }) => {
    await noteRefusals(browser);
    await app.open("/#/today");
    await expect(screen.getByLabel("Branch")).toContainText("Practitioner Branch");
    const nav = screen.getByRole("navigation", "Main");
    await expect(nav.getByRole("link", "Expenses & pay")).toBeVisible();
    await expect(nav.getByRole("link", "Stock")).toBeHidden();
    await expect(nav.getByRole("link", "Sales reports")).toBeHidden();

    await nav.getByRole("link", "Expenses & pay").tap();
    await expect(screen.getByRole("heading", "Product sales")).toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: "Role test oil" }).first()).toBeVisible();
    await new Promise((done) => setTimeout(done, 700));
    expect(await refused(browser)).toEqual([]);
});
