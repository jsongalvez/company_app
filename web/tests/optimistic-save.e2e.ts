import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

type Screen = Parameters<Parameters<typeof test>[1]>[0]["screen"];

/** On Today, clocked in: fill a session for a brand-new client and tap Add session. Returns the client's name. */
async function addSession(screen: Screen): Promise<string> {
    const first = `Opt${Date.now().toString().slice(-6)}`;
    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", "New session");
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "F" });
    await form.getByLabel(/^Age/).fill("40");
    await form.getByRole("button", "Create client").tap();
    await expect(dialog.getByText(`${first} Tester`, { exact: false })).toBeVisible();
    await expect(dialog.getByLabel(/^Price/)).not.toHaveValue("");
    await dialog.getByRole("button", "Add session").tap();
    return `${first} Tester`;
}

// User (Nov 4): a saved session shows on Today the moment Save is tapped; the server call runs behind it.
test("a new session shows on Today before the server answers, once", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    let release = () => {};
    const answered = new Promise<void>((r) => (release = r));
    let posts = 0;
    await browser.route(/\/api\/sessions(\?|$)/, async (route) => {
        if (route.request.method !== "POST") return route.fallback();
        posts++;
        await answered;
        await route.fallback();
    });
    const name = await addSession(screen);
    const rows = screen.getByRole("row").filter({ hasText: name });
    await expect(rows).toHaveCount(1, { timeout: 2000 });
    expect(posts).toBe(1);
    release();
    await expect(rows.getByRole("button", "Done")).toBeVisible({ timeout: 15_000 });
    await expect(rows).toHaveCount(1);
    expect(posts).toBe(1);
});

// A refusal is never silent: the row stays on Today, says why, survives a reload, and Retry sends it.
test("a refused session stays on Today, marked, and Retry sends it", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    const uploads: string[] = [];
    await browser.route(/\/api\/telemetry$/, (route) => (uploads.push(route.request.postData ?? ""), route.fallback()));
    await browser.route(/\/api\/sessions(\?|$)/, (route) =>
        route.request.method === "POST" ? route.fulfill({ status: 400, json: { error: "Price is out of range" } }) : route.fallback(),
    );
    const name = await addSession(screen);
    const row = screen.getByRole("row").filter({ hasText: name });
    await expect(row.getByText("Couldn't save")).toBeVisible({ timeout: 10_000 });
    await expect(row.getByText("Price is out of range")).toBeVisible();
    await expect.poll(() => uploads.some((b) => b.includes('\\"outbox\\":\\"POST /api/sessions\\",\\"s\\":400')), { timeout: 10_000 }).toBe(true);
    await browser.unroute(/\/api\/sessions(\?|$)/);
    await app.open("/#/today");
    await expect(row.getByText("Couldn't save")).toBeVisible({ timeout: 10_000 });
    await row.getByRole("button", "Retry").tap();
    await expect(row.getByRole("button", "Done")).toBeVisible({ timeout: 15_000 });
    await expect(row.getByText("Couldn't save")).not.toBeVisible();
    await expect(screen.getByRole("row").filter({ hasText: name })).toHaveCount(1);
});
