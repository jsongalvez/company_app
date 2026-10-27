// Counts the /api calls the phone web app makes per flow, in a real browser at 375px with added latency.
//   node tools/perf/calls.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass (or CALLS_USER and CALLS_PASS) [--rtt 150] [--runs 3] [--idle 60] [--json out.json]
// Flows: sign-in (to the first Today), Finance open, Today again after 20s, Today -> Finance -> Today twice,
// tab hidden then visible, window blur then focus, save one session, mark it Done, reload the app, idle.
// Prints calls, rounds (calls that could not start before an earlier one ended) and the endpoint list per flow.
// Saving a session writes one session for a client named "Probe" (made on first use): use a test or probe account.
import { createRequire } from "node:module";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const arg = (name, fallback) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg("base");
const USER = arg("user", process.env.CALLS_USER ?? "owner");
const PASS = arg("pass", process.env.CALLS_PASS ?? "owner-dev-pass");
const RTT = Number(arg("rtt", "150"));
const RUNS = Number(arg("runs", "3"));
const IDLE_S = Number(arg("idle", "60"));
const OUT = arg("json");
const NO_SAVE = process.argv.includes("--no-save");
if (!BASE) {
    console.error("--base is required");
    process.exit(2);
}
const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const { chromium } = createRequire(join(repo, "web/package.json"))(
    "playwright-core",
);

const route = (u) =>
    u.pathname.replace(
        /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g,
        ":id",
    );

async function oneRun() {
    const browser = await chromium.launch();
    const ctx = await browser.newContext({
        viewport: { width: 375, height: 812 },
        timezoneId: "Asia/Manila",
    });
    const page = await ctx.newPage();
    const cdp = await ctx.newCDPSession(page);
    await cdp.send("Network.enable");
    await cdp.send("Network.emulateNetworkConditions", {
        offline: false,
        latency: RTT,
        downloadThroughput: (1.5e6 / 8) * 10,
        uploadThroughput: (750e3 / 8) * 10,
    });
    page.on("dialog", (d) => d.accept());

    let calls = [];
    let open = 0;
    let lastActivity = Date.now();
    const byReq = new Map();
    page.on("request", (r) => {
        const u = new URL(r.url());
        if (!u.pathname.startsWith("/api/") && u.pathname !== "/auth/login")
            return;
        const c = {
            what: `${r.method()} ${route(u)}`,
            start: Date.now(),
            end: NaN,
        };
        byReq.set(r, c);
        calls.push(c);
        open++;
        lastActivity = Date.now();
    });
    const done = (r) => {
        const c = byReq.get(r);
        if (!c) return;
        c.end = Date.now();
        open--;
        lastActivity = Date.now();
    };
    page.on("requestfinished", done);
    page.on("requestfailed", done);

    const settle = async (quietMs = 1500, maxMs = 20000) => {
        const t = Date.now();
        while (Date.now() - t < maxMs) {
            await page.waitForTimeout(100);
            if (open === 0 && Date.now() - lastActivity >= quietMs) return;
        }
    };
    const rounds = (cs) => {
        let n = 0;
        let end = -Infinity;
        for (const c of [...cs].sort((a, b) => a.start - b.start)) {
            if (c.start >= end) {
                n++;
                end = c.end;
            } else end = Math.max(end, c.end);
        }
        return n;
    };
    const flows = {};
    const flow = async (name, act) => {
        calls = [];
        const t0 = Date.now();
        await act();
        const cs = calls.slice();
        const last = cs.length ? Math.max(...cs.map((c) => c.end - t0)) : 0;
        flows[name] = {
            calls: cs.length,
            rounds: rounds(cs),
            lastMs: last,
            list: cs.map((c) => c.what),
        };
    };
    const go = async (hash) => {
        await page.evaluate((h) => (location.hash = h), hash);
        await settle();
    };

    await page.goto(BASE + "/");
    await page.getByLabel("Username").fill(USER);
    await page.getByLabel("Password").fill(PASS);
    await flow("sign-in to Today", async () => {
        await page.getByRole("button", { name: "Sign in" }).click();
        await page
            .getByRole("banner")
            .getByRole("button", { name: /Clock (in|out)/ })
            .first()
            .waitFor({ timeout: 20000 });
        await settle();
    });

    const bar = page.getByRole("banner");
    if (await bar.getByRole("button", { name: "Clock in" }).count()) {
        await bar.getByRole("button", { name: "Clock in" }).first().click();
        await bar
            .getByRole("button", { name: /Clock out/ })
            .first()
            .waitFor({ timeout: 20000 });
        await settle();
    }

    await flow("open Finance", () => go("#/finance"));
    await page.waitForTimeout(20000);
    await flow("Today again after 20s", () => go("#/today"));
    await flow(
        "Today -> Finance -> Today (Finance older than 15s)",
        async () => {
            await go("#/finance");
            await go("#/today");
        },
    );
    await flow("Today -> Finance -> Today again at once", async () => {
        await go("#/finance");
        await go("#/today");
    });
    await flow("tab hidden then visible", async () => {
        await page.evaluate(() => {
            const set = (v) =>
                Object.defineProperty(document, "visibilityState", {
                    value: v,
                    configurable: true,
                });
            set("hidden");
            document.dispatchEvent(new Event("visibilitychange"));
            set("visible");
            document.dispatchEvent(new Event("visibilitychange"));
        });
        await settle();
    });
    await flow("window blur then focus", async () => {
        await page.evaluate(() => {
            window.dispatchEvent(new Event("blur"));
            window.dispatchEvent(new Event("focus"));
        });
        await settle();
    });

    if (!NO_SAVE) {
        // A client may hold one waiting session only: finish a leftover one from an earlier run first (not counted).
        const doneBtn = page
            .getByRole("row")
            .filter({ hasText: "Probe" })
            .getByRole("button", { name: "Done" });
        while (await doneBtn.count()) {
            await doneBtn.first().click();
            await settle();
        }
        await page.getByRole("button", { name: "New session" }).first().click();
        const dialog = page.getByRole("dialog", { name: "New session" });
        await dialog
            .getByPlaceholder("Search by name or phone (2+ letters)")
            .fill("Probe");
        const row = dialog
            .getByRole("row")
            .filter({ hasText: "Probe" })
            .first();
        if (
            !(await row.waitFor({ timeout: 4000 }).then(
                () => true,
                () => false,
            ))
        ) {
            await dialog.getByRole("button", { name: "New client" }).click();
            const form = page.getByRole("dialog", { name: "New client" });
            await form.getByLabel(/^Last name/).fill("Client");
            await form.getByLabel(/^First name/).fill("Probe");
            await form.getByLabel(/^Gender/).selectOption({ value: "F" });
            await form.getByLabel(/^Age/).fill("30");
            await form.getByRole("button", { name: "Create client" }).click();
        } else await row.click();
        await dialog.getByText("Probe", { exact: false }).first().waitFor();
        await page.waitForFunction(() => {
            const el = [
                ...document.querySelectorAll("dialog[open] input"),
            ].find((i) => /price/i.test(i.labels?.[0]?.textContent ?? ""));
            return !!el && el.value !== "";
        });
        await settle();
        await flow("save a session", async () => {
            await dialog.getByRole("button", { name: "Add session" }).click();
            await dialog.waitFor({ state: "hidden", timeout: 15000 });
            await settle();
        });
        await flow("mark the session Done", async () => {
            const row = page
                .getByRole("row")
                .filter({ hasText: "Probe" })
                .filter({ has: page.getByRole("button", { name: "Done" }) })
                .first();
            await row.getByRole("button", { name: "Done" }).click();
            await settle();
        });
    }

    await flow("reload the app (signed in)", async () => {
        await page.reload();
        await page
            .getByRole("banner")
            .getByRole("button", { name: /Clock (in|out)/ })
            .first()
            .waitFor({ timeout: 20000 });
        await settle();
    });
    await flow(`idle ${IDLE_S}s`, () => page.waitForTimeout(IDLE_S * 1000));
    await browser.close();
    return flows;
}

const results = [];
for (let i = 1; i <= RUNS; i++) {
    const f = await oneRun();
    results.push(f);
    console.log(`\n== run ${i}  (${BASE}, rtt ${RTT}ms)`);
    for (const [name, v] of Object.entries(f)) {
        console.log(
            `${name}: ${v.calls} calls, ${v.rounds} rounds, last answer ${v.lastMs} ms`,
        );
        const counts = new Map();
        for (const w of v.list) counts.set(w, (counts.get(w) ?? 0) + 1);
        for (const [w, n] of counts)
            console.log(`    ${n > 1 ? `${n}x ` : ""}${w}`);
    }
}
console.log("\n== calls per run");
for (const name of Object.keys(results[0]))
    console.log(
        `${name.padEnd(36)} ${results.map((r) => r[name]?.calls ?? "-").join(" ")}`,
    );
if (OUT) writeFileSync(OUT, JSON.stringify(results, null, 2));
