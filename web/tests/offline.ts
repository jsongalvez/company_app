import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { addDoneSessionForNewClient, clockInHere, clockOutHere, diskValues } from "./support";

// The phone cut off from the server, and one person's day of work, for offline-day.e2e.ts and offline-hours.e2e.ts.

export type Browser = Parameters<Parameters<typeof test>[1]>[0]["browser"];
export type Screen = Parameters<Parameters<typeof test>[1]>[0]["screen"];
export type App = Parameters<Parameters<typeof test>[1]>[0]["app"];

export const BASE = process.env.APP_URL ?? "http://localhost:18080";
export const SERVER = /\/(api\/|auth\/|health)/;
export const sleep = (ms: number) => new Promise((done) => setTimeout(done, ms));
export type Net = "offline" | "flaky";

/** Send the request the phone made straight to the server, as if the wifi carried it there. */
async function deliver(r: { url: string; method: string; headers: Readonly<Record<string, string>>; postData?: string }) {
    const headers = Object.fromEntries(Object.entries(r.headers).filter(([k]) => !["host", "content-length", "connection", "origin", "referer"].includes(k)));
    await fetch(r.url, { method: r.method, headers, body: r.postData }).catch(() => {});
}

export async function cut(browser: Browser, net: Net) {
    if (net === "offline") return browser.route(SERVER, (route) => route.abort());
    let n = 0;
    await browser.route(SERVER, async (route) => {
        const req = route.request;
        const turn = n++ % 4;
        if (req.method === "GET") {
            if (turn === 1) await sleep(3_000);
            return route.continue().catch(() => {});
        }
        // Reaches the server, the answer is lost on the way back.
        if (turn === 0) return deliver(req).then(() => route.abort());
        // Reaches the server after the phone stopped waiting for it.
        if (turn === 1) return sleep(8_000).then(() => deliver(req).then(() => route.abort().catch(() => {})));
        if (turn === 2) return route.abort();
        return route.continue().catch(() => {});
    });
}

/** The phone's queue: saves not yet taken by the server. */
export async function waitingOnPhone(browser: Browser): Promise<number> {
    const items = JSON.parse((await diskValues(browser, "outbox"))[0] ?? "[]") as { sentAt?: number; failed?: string }[];
    return items.filter((i) => i.sentAt === undefined).length;
}

/** A sign-in token that ran out hours ago: the phone must renew it from its replay token before sending. */
export async function expireSignIn(browser: Browser) {
    await browser.evaluate(() => {
        const t = localStorage.getItem("token") ?? "";
        const [h, p] = t.split(".");
        const claims = JSON.parse(atob(p!.replace(/-/g, "+").replace(/_/g, "/"))) as Record<string, unknown>;
        const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
        localStorage.setItem("token", `${h}.${b64({ ...claims, exp: Math.floor(Date.now() / 1000) - 3600 })}.expired`);
    });
}

export async function pickBranch(app: App, screen: Screen, browser: Browser, branch: string) {
    await app.open("/#/today");
    const picker = screen.getByLabel("Branch");
    await expect(picker.getByRole("option", branch)).toBeAttached();
    await picker.selectOption({ label: branch });
    // The chosen option's text, read the same way in every engine (a select's own text differs by browser).
    const chosen = () =>
        browser.evaluate(() => document.querySelector<HTMLSelectElement>('select[aria-label="Branch"]')?.selectedOptions[0]?.textContent ?? "");
    await expect.poll(chosen).toBe(branch);
    await expect(
        screen
            .getByRole("banner")
            .getByRole("button", /Clock (in|out)/)
            .first(),
    ).toBeVisible();
}

/** One person's day: clock in, a session for a new client marked done, a product sale, an expense, pay (coordinator), clock out. */
export async function workDay(app: App, screen: Screen, browser: Browser, payee: string | null) {
    const nav = screen.getByRole("navigation", "Main");
    await clockInHere(screen, browser);
    await addDoneSessionForNewClient(screen);

    await nav.getByRole("link", "Stock").tap();
    await screen.getByRole("button", "Product sale").tap();
    const sale = screen.getByRole("dialog", "Product sale");
    const option = () =>
        browser.evaluate(
            () => [...document.querySelectorAll("dialog[open] option")].map((o) => o.textContent ?? "").find((t) => t.startsWith("Offline test oil")) ?? "",
        );
    await expect.poll(option, { timeout: 20_000 }).toMatch(/^Offline test oil/);
    await sale.getByLabel(/^Product/).selectOption({ label: await option() });
    await sale.getByLabel(/^Quantity/).fill("1");
    await sale.getByRole("button", "Record sale").tap();
    await expect(sale).toBeHidden({ timeout: 20_000 });

    await nav.getByRole("link", "Expenses & pay").tap();
    await screen.getByRole("button", "Add").first().tap();
    const expense = screen.getByRole("dialog", "Add expense");
    await expense.getByLabel(/^Category/).selectOption({ value: "WATER" });
    await expense.getByLabel(/^Amount/).fill("150");
    await expense.getByLabel(/^Notes/).fill("Water");
    await expense.getByRole("button", "Save").tap();
    await expect(expense).toBeHidden({ timeout: 20_000 });

    if (payee) {
        await screen.getByRole("button", "Add").nth(1).tap();
        const pay = screen.getByRole("dialog", "Add pay");
        await pay.getByLabel(/^Person/).selectOption({ label: payee });
        await pay.getByLabel(/^Amount/).fill("300");
        await pay.getByLabel(/^Note/).fill("Day pay");
        await pay.getByRole("button", "Save").tap();
        await expect(pay).toBeHidden({ timeout: 20_000 });
    }

    await app.open("/#/today");
    await clockOutHere(screen, browser);
}

/** The superuser (tools/seed/roles.sql) reads every branch's totals and commission; a practitioner may not. */
let ownerToken: string | undefined;
export async function owner(): Promise<string> {
    if (ownerToken) return ownerToken;
    const res = await fetch(`${BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: "role-superuser", password: "staff-dev-pass" }),
    });
    expect(res.status).toBe(200);
    ownerToken = ((await res.json()) as { token: string }).token;
    return ownerToken;
}

export const branchIdOf = (browser: Browser) => browser.evaluate(() => localStorage.getItem("branchId") ?? "");
