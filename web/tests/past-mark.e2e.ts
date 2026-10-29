import { test } from "@e2e-dev/web";
import { expect } from "e2e";

type Screen = Parameters<Parameters<typeof test>[2]>[0]["screen"];

const BASE = process.env.APP_URL ?? "http://localhost:18080";
const down = /\/(api\/|health)/;

async function addSessionForNewClient(screen: Screen, first: string, offlinePrice?: string) {
    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", /^New session on /);
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "F" });
    await form.getByLabel(/^Age/).fill("40");
    await form.getByRole("button", "Create client").tap();
    if (offlinePrice) await dialog.getByLabel(/^Price/).fill(offlinePrice);
    else await expect(dialog.getByLabel(/^Price/)).not.toHaveValue("");
    await dialog.getByRole("button", "Add session").tap();
    await expect(dialog).toHaveCount(0);
}

async function giveReason(screen: Screen, reason: string) {
    const ask = screen.getByRole("dialog", "Reason for change");
    await expect(ask).toBeVisible();
    await ask.getByLabel("Reason").fill(reason);
    await ask.getByRole("button", "Save").tap();
    await expect(ask).toHaveCount(0);
}

// Feedback 60054711: marking a session on an earlier day asks for the reason first, for anyone in the branch.
test("a practitioner marks yesterday's session done after giving a reason", { session: "role-practitioner" }, async ({ app, screen }) => {
    const first = `Mark${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("button", "Today")).toBeVisible();
    await addSessionForNewClient(screen, first);
    const row = screen.getByRole("row").filter({ hasText: `${first} Tester` });
    await row.getByRole("button", "Done").tap();
    await giveReason(screen, "Forgot to tap Done");
    await expect(row.getByText("Done")).toBeVisible();
    await expect(row.getByRole("button", "Undo")).toBeVisible();
});

// Owed #19: anyone in the branch fixes an earlier day's session, with a reason the change history keeps.
test("a practitioner changes yesterday's session price after giving a reason", { session: "role-practitioner" }, async ({ app, screen, browser }) => {
    const first = `Fix${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("button", "Today")).toBeVisible();
    await addSessionForNewClient(screen, first);
    await screen
        .getByRole("row")
        .filter({ hasText: `${first} Tester` })
        .tap();
    await screen.getByRole("button", "Change").first().tap();
    const dialog = screen.getByRole("dialog", "Change price");
    await dialog.getByLabel("Final price").fill("1999");
    await dialog.getByRole("button", "Save").tap();
    await giveReason(screen, "Gave a discount");
    await expect(screen.getByText("1,999.00")).toBeVisible();
    const id = (await browser.evaluate(() => location.hash)).split("/").pop()!;
    expect(JSON.stringify(await ownerAudit(id))).toContain("Gave a discount");
});

async function ownerAudit(sessionId: string) {
    const login = await fetch(`${BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: "owner", password: process.env.E2E_OWNER_PASSWORD ?? "owner-dev-pass" }),
    });
    const { token } = (await login.json()) as { token: string };
    const audit = await fetch(`${BASE}/api/audit-log?tableName=session&recordId=${sessionId}`, { headers: { Authorization: `Bearer ${token}` } });
    expect(audit.status).toBe(200);
    return audit.json();
}

// No signal on an earlier day: the session and its mark stay on the day picked, with the reason typed.
test("an offline session and mark on yesterday land on yesterday", { session: "role-practitioner" }, async ({ app, screen, browser }) => {
    const first = `Off${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await screen.getByRole("button", "Previous day").tap();
    await expect(screen.getByRole("button", "Today")).toBeVisible();
    await expect(screen.getByRole("button", "New session")).toBeVisible();

    await browser.route(down, (route) => route.abort());
    await addSessionForNewClient(screen, first, "2500");
    const row = screen.getByRole("row").filter({ hasText: `${first} Tester` });
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 20_000 });
    await browser.unroute(down);
    await expect(screen.getByText("waiting to send", { exact: false })).not.toBeVisible({ timeout: 20_000 });
    await expect(row).toBeVisible({ timeout: 15_000 });

    await browser.route(down, (route) => route.abort());
    await row.getByRole("button", "Done").tap();
    await giveReason(screen, "Phone had no signal");
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 20_000 });
    await browser.unroute(down);
    await expect(screen.getByText("waiting to send", { exact: false })).not.toBeVisible({ timeout: 20_000 });
    await expect(screen.getByText("couldn't be sent", { exact: false })).toHaveCount(0);

    await browser.reload();
    const saved = screen.getByRole("row").filter({ hasText: `${first} Tester` });
    await expect(saved.getByText("Done")).toBeVisible();
    await screen.getByRole("button", "Today").tap();
    await expect(screen.getByRole("button", "Today")).toHaveCount(0);
    await expect(screen.getByRole("row").filter({ hasText: `${first} Tester` })).toHaveCount(0);

    await screen.getByRole("button", "Previous day").tap();
    await screen
        .getByRole("row")
        .filter({ hasText: `${first} Tester` })
        .tap();
    const id = (await browser.evaluate(() => location.hash)).split("/").pop()!;
    // The history is for coordinators: read it as the owner.
    expect(JSON.stringify(await ownerAudit(id))).toContain("Phone had no signal");
});
