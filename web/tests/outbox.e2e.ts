import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

// No signal: a front-desk save waits on the phone, then goes out by itself once the server answers.
test("a save made offline is sent when the signal is back", { session: "owner" }, async ({ app, screen, browser }) => {
    const note = `Offline water ${Date.now().toString().slice(-6)}`;
    await app.open("/#/finance");
    await expect(screen.getByRole("heading", "Expenses & pay")).toBeVisible();
    const down = /\/(api\/|health)/;
    await browser.route(down, (route) => route.abort());
    await screen.getByRole("button", "Add").first().tap();
    const dialog = screen.getByRole("dialog", "Add expense");
    await dialog.getByLabel(/^Category/).selectOption({ value: "WATER" });
    await dialog.getByLabel(/^Amount/).fill("150");
    await dialog.getByLabel(/^Notes/).fill(note);
    await dialog.getByRole("button", "Save").tap();
    await expect(screen.getByText("Saved on this phone", { exact: false })).toBeVisible({ timeout: 20_000 });
    await expect(screen.getByText("1 change waiting to send", { exact: false })).toBeVisible();
    await browser.unroute(down);
    await expect(screen.getByText("waiting to send", { exact: false })).not.toBeVisible({ timeout: 20_000 });
    await expect(screen.getByRole("row").filter({ hasText: note })).toBeVisible({ timeout: 15_000 });
});

const BASE = process.env.APP_URL ?? "http://localhost:18080";
const down = /\/(api\/|health)/;

// A mark made with no signal shows straight away, flagged "Not sent", and goes out by itself.
test("an offline mark shows at once and sends later", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen);
    const row = screen.getByRole("row").filter({ hasText: name });
    await row.getByRole("button", "Undo").tap();
    await expect(row.getByText("Waiting")).toBeVisible();
    await browser.route(down, (route) => route.abort());
    await row.getByRole("button", "Done").tap();
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 20_000 });
    await expect(row.getByText("Done")).toBeVisible();
    await browser.unroute(down);
    await expect(row.getByText("Not sent")).not.toBeVisible({ timeout: 20_000 });
    await expect(row.getByText("Done")).toBeVisible();
});

// Someone else changes the session while this phone is offline: the waiting mark is refused, not forced through.
test("a mark that clashes with someone else's change lands in couldn't send", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen);
    const row = screen.getByRole("row").filter({ hasText: name });
    await row.getByRole("button", "Undo").tap();
    await expect(row.getByText("Waiting")).toBeVisible();
    const token = await browser.evaluate(() => localStorage.getItem("token"));
    const branchId = await browser.evaluate(() => localStorage.getItem("branchId"));
    await browser.route(down, (route) => route.abort());
    await row.getByRole("button", "Done").tap();
    await expect(row.getByText("Not sent")).toBeVisible({ timeout: 20_000 });

    // Another desk marks it Cancelled meanwhile (straight to the server, not through this phone).
    const auth = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
    const dash = (await (await fetch(`${BASE}/api/branches/${branchId}/dashboard/today`, { headers: auth })).json()) as {
        sessions: { id: string; clientName: string | null; version: number }[];
    };
    const s = dash.sessions.find((x) => x.clientName === name)!;
    const r = await fetch(`${BASE}/api/sessions/${s.id}/status`, {
        method: "PATCH",
        headers: auth,
        body: JSON.stringify({ status: "CANCELLED", version: s.version }),
    });
    expect(r.status).toBe(200);

    await browser.unroute(down);
    await expect(screen.getByText("couldn't be sent", { exact: false }).first()).toBeVisible({ timeout: 30_000 });
    await expect(row.getByText("Cancelled")).toBeVisible({ timeout: 15_000 });
});
