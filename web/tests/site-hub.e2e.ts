import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { execFileSync } from "node:child_process";
import { connect, createServer, type Server } from "node:net";
import { request } from "node:https";
import { chromium, firefox, webkit, type Browser, type BrowserContext, type Page } from "playwright-core";
import { businessDate } from "./clock";
import { owner } from "./offline";

// A provincial tour with no signal (owner, 2026-11-21): four phones on the laptop's hotspot see each other's
// unsent work through the site hub (hub/), one phone that never joined shows as not seen, the hub is killed
// and refilled by the phones, and once the signal is back the cloud holds what the same work done online
// holds. The relay runs in its container (hub/e2e-up.sh) as https://hub.kiel.party with a test cert; each
// phone's browser reaches that name through a proxy, as a phone on the hotspot reaches the laptop. CI: the
// "site hub" job in browser.yml, the only one with all three engines and the relay.

const BASE = process.env.APP_URL ?? "http://localhost:18080";
const HUB_PORT = Number(process.env.HUB_PORT ?? 8443);
const HUB_CONTAINER = process.env.HUB_CONTAINER ?? "company-hub-e2e";
const ENGINES = { chromium, webkit, firefox };
/** Which engine each connected phone runs; HUB_ENGINES narrows it on a box missing some (CI runs all three). */
const PHONE_ENGINES = (process.env.HUB_ENGINES ?? "chromium,webkit,firefox,chromium").split(",") as (keyof typeof ENGINES)[];

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** An HTTP proxy that sends hub.kiel.party:443 to the relay's container, and anything else where it asked to go. */
function hotspot(): Promise<{ server: Server; url: string }> {
    const server = createServer((client) => {
        client.once("data", (head) => {
            const line = head.toString("latin1").split("\r\n")[0] ?? "";
            const [method, target] = line.split(" ");
            if (method === "CONNECT") {
                const [host, port] = (target ?? "").split(":");
                const to = host === "hub.kiel.party" ? { host: "127.0.0.1", port: HUB_PORT } : { host: host!, port: Number(port) };
                const up = connect(to, () => {
                    client.write("HTTP/1.1 200 Connection Established\r\n\r\n");
                    up.pipe(client).pipe(up);
                });
                up.on("error", () => client.destroy());
                client.on("error", () => up.destroy());
                return;
            }
            const u = new URL(target ?? "");
            const up = connect({ host: u.hostname, port: Number(u.port || 80) }, () => {
                up.write(head.toString("latin1").replace(target!, u.pathname + u.search));
                up.pipe(client).pipe(up);
            });
            up.on("error", () => client.destroy());
            client.on("error", () => up.destroy());
        });
    });
    return new Promise((done) => server.listen(0, "127.0.0.1", () => done({ server, url: `http://127.0.0.1:${(server.address() as { port: number }).port}` })));
}

/** What the relay holds for a room, read as a phone would (it can't read inside). */
function hubRecords(room: string): Promise<number> {
    const body = JSON.stringify({ device: "test-probe", since: 0, boot: "", records: [] });
    return new Promise((done, fail) => {
        const req = request(
            {
                host: "127.0.0.1",
                port: HUB_PORT,
                servername: "hub.kiel.party",
                path: `/v1/rooms/${room}/sync`,
                method: "POST",
                rejectUnauthorized: false,
                headers: { "Content-Type": "application/json" },
            },
            (res) => {
                let s = "";
                res.on("data", (c) => (s += c));
                res.on("end", () => (res.statusCode === 200 ? done((JSON.parse(s) as { records: unknown[] }).records.length) : fail(new Error(s))));
            },
        );
        req.on("error", fail);
        req.end(body);
    });
}

type Phone = { name: string; user: string; page: Page; context: BrowserContext };

async function phone(browser: Browser, user: string, name: string, proxy: string | null): Promise<Phone> {
    const context = await browser.newContext({
        ...(proxy ? { proxy: { server: proxy, bypass: "localhost,127.0.0.1" } } : {}),
        ignoreHTTPSErrors: true,
        serviceWorkers: "block",
        timezoneId: "Asia/Manila",
        viewport: { width: 1200, height: 900 },
    });
    // The unconnected phone is not on the hotspot: the hub's name leads nowhere for it.
    if (!proxy) await context.route("https://hub.kiel.party/**", (r) => r.abort());
    const page = await context.newPage();
    page.on("dialog", (d) => void d.accept());
    const signedIn = page
        .getByRole("banner")
        .getByRole("button", { name: /Clock (in|out)/ })
        .first();
    for (let tries = 0; ; tries++) {
        await page.goto(`${BASE}/`);
        await page.getByLabel("Username").fill(user);
        await page.getByLabel("Password").fill("staff-dev-pass");
        await page.getByRole("button", { name: "Sign in" }).click();
        const refused = page.getByText(/^Too many tries/);
        await expect.poll(async () => (await signedIn.isVisible()) || (await refused.isVisible()), { timeout: 30_000 }).toBe(true);
        // The server takes 10 sign-ins a minute from one address.
        if (tries > 0 || !(await refused.isVisible())) break;
        await sleep(61_000);
    }
    return { name, user, page, context };
}

const shown = (l: ReturnType<Page["getByText"]>, timeout = 20_000) => expect.poll(() => l.isVisible(), { timeout }).toBe(true);

async function pick(p: Phone, branch: string) {
    await p.page.goto(`${BASE}/#/today`);
    const picker = p.page.getByLabel("Branch");
    await expect.poll(() => picker.locator("option", { hasText: branch }).count(), { timeout: 20_000 }).toBeGreaterThan(0);
    await picker.selectOption({ label: branch });
    await expect.poll(() => p.page.evaluate(() => localStorage.getItem("branchId") ?? "")).not.toBe("");
    await shown(
        p.page
            .getByRole("banner")
            .getByRole("button", { name: /Clock (in|out)/ })
            .first(),
    );
}

async function clock(p: Phone, to: "in" | "out") {
    const bar = p.page.getByRole("banner");
    await shown(bar.getByRole("button", { name: /Clock (in|out)/ }).first());
    const button = bar.getByRole("button", { name: to === "in" ? "Clock in" : /Clock out/ });
    if ((await button.count()) > 0) await button.first().click();
    await shown(bar.getByRole("button", { name: to === "in" ? /Clock out/ : "Clock in" }).first());
}

/** A's part of the day: a waiting session for a brand-new client and a product sale. Returns the client's name. */
async function sessionAndSale(p: Phone, tag: string): Promise<string> {
    const { page } = p;
    const first = `Hub${tag}${Date.now().toString().slice(-5)}`;
    await page.getByRole("button", { name: "New session" }).click();
    const dialog = page.getByRole("dialog", { name: "New session" });
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", { name: "New client" }).click();
    const form = page.getByRole("dialog", { name: "New client" });
    await form.getByLabel(/^Last name/).fill("Site");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "F" });
    await form.getByLabel(/^Age/).fill("40");
    await form.getByRole("button", { name: "Create client" }).click();
    await shown(dialog.getByText(`${first} Site`));
    await expect.poll(() => dialog.getByLabel(/^Price/).inputValue(), { timeout: 20_000 }).not.toBe("");
    await dialog.getByRole("button", { name: "Add session" }).click();
    await shown(page.getByRole("row").filter({ hasText: `${first} Site` }));

    await page.getByRole("button", { name: "Product sale" }).first().click();
    const sale = page.getByRole("dialog", { name: "Product sale" });
    const option = () =>
        page.evaluate(
            () => [...document.querySelectorAll("dialog[open] option")].map((o) => o.textContent ?? "").find((t) => t.startsWith("Offline test oil")) ?? "",
        );
    await expect.poll(option, { timeout: 20_000 }).toMatch(/^Offline test oil/);
    await sale.getByLabel(/^Product/).selectOption({ label: await option() });
    await sale.getByLabel(/^Quantity/).fill("2");
    await sale.getByRole("button", { name: "Record sale" }).click();
    await expect.poll(() => sale.isVisible(), { timeout: 20_000 }).toBe(false);
    return `${first} Site`;
}

/** The phone's own store (web/src/lib/disk.ts). */
function disk(p: Phone, key: string): Promise<string | null> {
    return p.page.evaluate(
        (k) =>
            new Promise<string | null>((resolve) => {
                const req = indexedDB.open("company-app", 1);
                req.onerror = () => resolve(null);
                req.onsuccess = () => {
                    const get = req.result.transaction("kv").objectStore("kv").get(k);
                    get.onsuccess = () => resolve((get.result as string | undefined) ?? null);
                    get.onerror = () => resolve(null);
                };
            }),
        key,
    );
}

async function unsent(p: Phone): Promise<number> {
    const items = JSON.parse((await disk(p, "outbox")) ?? "[]") as { sentAt?: number; failed?: string }[];
    return items.filter((i) => i.sentAt === undefined).length;
}

const cloud = (url: string) => url.startsWith(BASE) && /\/(api\/|auth\/|health)/.test(new URL(url).pathname + "/");

/** What the cloud holds for a branch's day: money, sessions and who was on duty. */
async function totals(branchId: string) {
    const auth = { headers: { Authorization: `Bearer ${await owner()}` } };
    const get = async <T>(path: string) => (await (await fetch(`${BASE}${path}`, auth)).json()) as T;
    const date = businessDate();
    const sum = await get<{ branchDayId: string; grossIncome: string; totalProductSales: string; netIncome: string }>(
        `/api/branches/${branchId}/daily-summary?date=${date}`,
    );
    const sessions = await get<{ sessionType: string; sessionStatus: string; finalPrice: string }[]>(`/api/branches/${branchId}/sessions?date=${date}`);
    const users = sum.branchDayId ? await get<unknown[]>(`/api/branch-days/${sum.branchDayId}/users`) : [];
    return {
        gross: Number(sum.grossIncome ?? 0),
        products: Number(sum.totalProductSales ?? 0),
        net: Number(sum.netIncome ?? 0),
        sessions: sessions.map((s) => `${s.sessionType} ${s.sessionStatus} ${s.finalPrice}`),
        onDuty: users.length,
    };
}
/** What is in `after` and not in `before`, counting repeats. */
function added(before: string[], after: string[]): string[] {
    const left = [...before];
    return after.filter((x) => (left.includes(x) ? (left.splice(left.indexOf(x), 1), false) : true));
}
type Totals = Awaited<ReturnType<typeof totals>>;
const delta = (a: Totals, b: Totals) => ({
    gross: b.gross - a.gross,
    products: b.products - a.products,
    net: b.net - a.net,
    sessions: added(a.sessions, b.sessions).sort(),
    // Who worked the day, not a difference: a second run on the same day (only on this box) adds no one.
    onDuty: b.onDuty,
});

test("four phones on a site hub see each other's unsent work, the hub refills after a restart, and the cloud ends equal", { timeout: 900_000 }, async () => {
    const { server, url } = await hotspot();
    const browsers = new Map<string, Browser>();
    const engine = async (n: keyof typeof ENGINES) => browsers.get(n) ?? browsers.set(n, await ENGINES[n].launch()).get(n)!;
    try {
        const names = ["Hub Ana", "Hub Ben", "Hub Cora", "Hub Dan"];
        const phones: Phone[] = [];
        for (const [i, n] of names.entries()) phones.push(await phone(await engine(PHONE_ENGINES[i]!), `hub-${"abcd"[i]}`, n, url));
        const loner = await phone(await engine("chromium"), "hub-e", "Hub Eva", null);
        const [a, ...others] = phones as [Phone, ...Phone[]];
        const desk = "00000000-0000-4000-8000-000000000301";
        const field = "00000000-0000-4000-8000-000000000302";

        // Online: the day's work at Desk H.
        const deskBefore = await totals(desk);
        for (const p of phones) (await pick(p, "Desk H Branch"), await clock(p, "in"));
        await sessionAndSale(a, "D");
        for (const p of phones) await clock(p, "out");
        await expect.poll(async () => (await Promise.all(phones.map(unsent))).reduce((x, y) => x + y), { timeout: 60_000 }).toBe(0);
        const deskAfter = await totals(desk);

        // At the site: every phone pulls Site H with signal, then the signal goes.
        const fieldBefore = await totals(field);
        for (const p of [...phones, loner]) await pick(p, "Site H Branch");
        // Give the branch pull a moment to land, as a phone does between sign-in and walking out of signal.
        await sleep(3_000);
        for (const p of [...phones, loner])
            await p.context.route(
                (u) => cloud(u.href),
                (r) => r.abort(),
            );

        for (const p of phones) await clock(p, "in");
        const client = await sessionAndSale(a, "S");
        const at = Date.now();
        for (const b of others) {
            const panel = b.page.getByRole("region", { name: "Site" });
            await shown(panel.getByRole("list", { name: "Waiting at the site" }).getByText(client), 10_000);
            await shown(panel.getByRole("list", { name: "On duty at the site" }).getByText("Hub Ana"), 10_000);
            await shown(panel.getByRole("list", { name: "Sales at the site" }).getByText("2 sold · Hub Ana").last(), 10_000);
            await shown(panel.getByRole("list", { name: "Phones at the site" }).getByText("Hub Eva · not seen"), 10_000);
        }
        expect(Date.now() - at).toBeLessThan(10_000 * others.length);
        // The phone off the hotspot works as before: no Site panel.
        await sleep(3_000);
        expect(await loner.page.getByRole("region", { name: "Site" }).count()).toBe(0);

        // The laptop dies and comes back empty; the phones fill it again.
        const room = (JSON.parse((await disk(a, `site:ticket:${field}`))!) as { room: string }).room;
        const held = await hubRecords(room);
        expect(held).toBeGreaterThanOrEqual(4);
        execFileSync("docker", ["restart", "-t", "0", HUB_CONTAINER]);
        await expect.poll(() => hubRecords(room).catch(() => -1), { timeout: 30_000 }).toBeGreaterThanOrEqual(held);
        const b = others[0]!;
        await shown(b.page.getByRole("region", { name: "Site" }).getByText(/Site hub: connected/), 15_000);

        for (const p of phones) await clock(p, "out");

        // Signal back: each phone sends its own work; the cloud ends as it did for the same work online.
        for (const p of [...phones, loner]) await p.context.unrouteAll();
        for (const p of phones) await p.page.reload();
        await expect.poll(async () => (await Promise.all(phones.map(unsent))).reduce((x, y) => x + y), { timeout: 120_000 }).toBe(0);
        expect(delta(fieldBefore, await totals(field))).toEqual(delta(deskBefore, deskAfter));
    } finally {
        for (const br of browsers.values()) await br.close().catch(() => {});
        server.close();
    }
});
