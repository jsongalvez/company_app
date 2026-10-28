import { readFileSync } from "node:fs";
import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
import { clockOutHere, doneSessionForNewClient } from "./support";

type Browser = Parameters<Parameters<typeof test>[1]>[0]["browser"];

// One user per role, each on its own branch (tools/seed/roles.sql). `pay` and `remit` say what the
// role may do by design: a role without one must find the action missing and the server refusing it.
const roles = [
    { role: "superuser", pay: true, remit: true, inviteIn: 11 },
    { role: "owner", pay: true, remit: false, inviteIn: 12 },
    { role: "manager", pay: true, remit: true, inviteIn: 13 },
    { role: "coordinator", pay: true, remit: true, inviteIn: 14 },
    { role: "practitioner", pay: false, remit: false, inviteIn: 15 },
] as const;

/**
 * Notes every API request the app makes that the server refuses (4xx other than 401, or 5xx), in
 * sessionStorage so it survives reloads. A refused call the screen quietly ignores still reaches the
 * error tracker in production, so a role's normal day must have none.
 */
async function noteRefusals(browser: Browser) {
    await browser.addInitScript(() => {
        const w = window as unknown as { __plainFetch?: typeof fetch };
        const plain = window.fetch.bind(window);
        w.__plainFetch = plain;
        window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
            const res = await plain(input, init);
            const req = input instanceof Request ? input : null;
            const url = new URL(req ? req.url : String(input), location.href);
            if (url.pathname.startsWith("/api/") && res.status >= 400 && res.status !== 401) {
                const seen = JSON.parse(sessionStorage.getItem("refused") ?? "[]") as string[];
                seen.push(`${init?.method ?? req?.method ?? "GET"} ${url.pathname} ${res.status}`);
                sessionStorage.setItem("refused", JSON.stringify(seen));
            }
            return res;
        };
    });
}
const refused = (browser: Browser) => browser.evaluate(() => JSON.parse(sessionStorage.getItem("refused") ?? "[]") as string[]);

/** Status of a request made with the signed-in person's own token, as the app would send it. */
async function statusOf(browser: Browser, method: string, path: string, body?: Record<string, unknown>): Promise<number> {
    return browser.evaluate(
        async ([m, p, b]) => {
            const plain = (window as unknown as { __plainFetch?: typeof fetch }).__plainFetch ?? fetch;
            const res = await plain(p, {
                method: m,
                headers: { Authorization: `Bearer ${localStorage.getItem("token") ?? ""}`, "Content-Type": "application/json" },
                body: b ? JSON.stringify(b) : undefined,
            });
            return res.status;
        },
        [method, path, body ?? null] as [string, string, Record<string, unknown> | null],
    );
}

async function todayIds(browser: Browser): Promise<{ branchId: string; branchDayId: string }> {
    return browser.evaluate(async () => {
        const branchId = localStorage.getItem("branchId") ?? "";
        const res = await fetch(`/api/branches/${branchId}/today`, { headers: { Authorization: `Bearer ${localStorage.getItem("token") ?? ""}` } });
        return { branchId, branchDayId: ((await res.json()) as { branchDayId: string }).branchDayId };
    });
}

for (const r of roles) {
    const name = `Role ${r.role[0]!.toUpperCase()}${r.role.slice(1)}`;
    const branch = `${name.slice(5)} Branch`;
    const article = /^[aeiou]/.test(r.role) ? "an" : "a";

    test(
        `${article} ${r.role} works a day: clock in, session, sale, expense, pay, remittance, relief invite, Finance, Reports`,
        { session: `role-${r.role}` },
        async ({ app, screen, browser }) => {
            const tag = Date.now().toString().slice(-6);
            await noteRefusals(browser);
            await app.open("/#/today");
            await expect(screen.getByLabel("Branch")).toContainText(branch);
            const nav = screen.getByRole("navigation", "Main");

            // Clock in and log a session for a new client, marked done.
            const client = await doneSessionForNewClient(app, screen, browser);

            // A product sale from stock.
            await nav.getByRole("link", "Stock").tap();
            await screen.getByRole("button", "Product sale").tap();
            const sale = screen.getByRole("dialog", "Product sale");
            await sale.getByLabel(/^Product/).selectOption({ label: "Role test oil · 100.00 · 50 left" });
            await sale.getByLabel(/^Quantity/).fill("1");
            await sale.getByRole("button", "Record sale").tap();
            await expect(sale).toBeHidden();
            await expect(screen.getByRole("row").filter({ hasText: "Role test oil" }).getByText("49", { exact: true })).toBeVisible();

            // Finance: the session is listed, and an expense goes in.
            await nav.getByRole("link", "Expenses & pay").tap();
            await expect(screen.getByRole("row").filter({ hasText: client })).toBeVisible();
            const note = `Water ${tag}`;
            await screen.getByRole("button", "Add").first().tap();
            const expense = screen.getByRole("dialog", "Add expense");
            await expense.getByLabel(/^Category/).selectOption({ value: "WATER" });
            await expense.getByLabel(/^Amount/).fill("150");
            await expense.getByLabel(/^Notes/).fill(note);
            await expense.getByRole("button", "Save").tap();
            await expect(screen.getByRole("row").filter({ hasText: note })).toBeVisible();

            // Pay for the day's work.
            const { branchId, branchDayId } = await todayIds(browser);
            if (r.pay) {
                const payNote = `Pay ${tag}`;
                await screen.getByRole("button", "Add").nth(1).tap();
                const pay = screen.getByRole("dialog", "Add pay");
                await pay.getByLabel(/^Person/).selectOption({ label: name });
                await pay.getByLabel(/^Amount/).fill("300");
                await pay.getByLabel(/^Note/).fill(payNote);
                await pay.getByRole("button", "Save").tap();
                await expect(pay).toBeHidden();
                await expect(screen.getByRole("row").filter({ hasText: payNote })).toBeVisible();
            } else {
                await expect(screen.getByRole("heading", "Pay")).toBeHidden();
                const body = {
                    id: crypto.randomUUID(),
                    workBranchDayId: branchDayId,
                    payingBranchDayId: branchDayId,
                    userId: crypto.randomUUID(),
                    amount: "300",
                };
                expect(await statusOf(browser, "POST", "/api/compensation", body)).toBe(403);
            }

            // Reports: the accountant's report downloads.
            await nav.getByRole("link", "Sales reports").tap();
            await expect(screen.getByText("Report for your accountant")).toBeVisible();
            const xlsx = await browser.waitForDownload(() => screen.getByRole("button", "Download Excel").tap());
            expect(readFileSync(xlsx.absolutePath).subarray(0, 2).toString()).toBe("PK");

            // Relief: invite the relief worker for a later day at this branch.
            const day = businessDate(r.inviteIn);
            await nav.getByRole("link", "Relief duty").tap();
            await screen.getByRole("button", "Invite someone").tap();
            const invite = screen.getByRole("dialog", "Invite someone for relief");
            await invite.getByLabel(/^Day/).fill(day);
            await invite.getByLabel("Find person").fill("relief");
            await invite.getByRole("row").filter({ hasText: "Dev relief" }).tap();
            await invite.getByRole("button", "Send invite").tap();
            await expect(invite).toBeHidden();
            await expect(screen.getByRole("row").filter({ hasText: "Dev relief" })).toBeVisible();

            // Remittance: send today's takings to the office, then undo so the day stays open.
            const today = businessDate();
            if (r.remit) {
                await nav.getByRole("link", "Remittance").tap();
                await screen.getByRole("button", "New remittance").tap();
                const d = screen.getByRole("dialog", "New remittance");
                await d.getByLabel(/^From/).fill(today);
                await d.getByLabel(/^To/).fill(today);
                await d.getByRole("button", "Create draft").tap();
                await screen.getByRole("button", "Add everything in range").tap();
                await expect(screen.getByRole("row").filter({ hasText: client })).toBeVisible();
                await screen.getByRole("button", "Send to office").tap();
                await screen.getByRole("dialog", "Send remittance").getByRole("button", "Send to office").tap();
                await expect(screen.getByText("Sent", { exact: true }).first()).toBeVisible();
                await screen.getByRole("button", "Undo").tap();
                const u = screen.getByRole("dialog", "Undo remittance");
                await u.getByLabel(/^Reason/).fill("e2e test");
                await u.getByRole("button", "Return to draft").tap();
                await expect(screen.getByText("Draft", { exact: true }).first()).toBeVisible();
            } else {
                await expect(nav.getByRole("link", "Remittance")).toBeHidden();
                const body = { id: crypto.randomUUID(), type: "SESSION", method: "BANK_TRANSFER", branchId, dateRangeStart: today, dateRangeEnd: today };
                expect(await statusOf(browser, "POST", "/api/remittances", body)).toBe(403);
            }

            // Clock out.
            await app.open("/#/today");
            await clockOutHere(screen, browser);
            await expect(screen.getByRole("banner").getByRole("button", "Clock in").first()).toBeVisible();
            expect(await refused(browser)).toEqual([]);
        },
    );
}

// The invitee's side, once: the relief worker accepts the invite tools/seed/relief-invite.sql left them.
test("the relief worker accepts an invite", { session: "relief" }, async ({ app, screen, browser }) => {
    await noteRefusals(browser);
    await app.open("/#/relief");
    const invite = screen.getByRole("row").filter({ hasText: "K6 Fixture Branch" });
    await expect(invite.getByText("from Dev staff")).toBeVisible();
    await invite.getByRole("button", "Accept").tap();
    await expect(invite.getByRole("button", "Accept")).toBeHidden();
    expect(await refused(browser)).toEqual([]);
});
