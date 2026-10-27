// Frozen slow-hotspot harness: what a phone on a weak mobile hotspot waits for, per screen.
//   node tools/perf/hotspot.mjs --base URL --user U --pass P [--runs 5] [--rtt 300] [--down 800] [--up 300] [--no-save] [--json out.json]
// Profile: 375px phone, `rtt` ms added latency, `down`/`up` kbit/s. Chromium's emulation cannot drop packets,
// so "slight loss" is not simulated (a lost packet costs about one more RTT; the RTT figure stands in for it).
// Per flow it prints: usable (ms from the tap until the screen shows content with no spinner),
// settled (ms until the last /api answer), /api calls, rounds (calls that had to wait for an earlier one),
// bytes over the wire (api = /api and /auth, all = everything the flow loaded, headers included in neither).
// Runs are medians; the spread (min-max) is the noise band. The profile and flows are fixed: change them only in a PR that says so.
// --shaper sends every byte through shaper.mjs (same profile, but connection set-up, TLS and service-worker
// fetches pay it too); --dist DIR serves the page and /assets from a local build, to A/B a web change against
// the live API. Both are off by default, so default runs stay comparable with the baseline.
// Saving a session writes one session for a client named "Probe" (made on first use): use a test or probe account.
import { createRequire } from "node:module";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { readFileSync, existsSync } from "node:fs";
import { startShaper } from "./shaper.mjs";

const arg = (name, fallback) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : fallback;
};
let BASE = arg("base");
const USER = arg("user", process.env.HOTSPOT_USER ?? "owner");
const PASS = arg("pass", process.env.HOTSPOT_PASS ?? "owner-dev-pass");
const RTT = Number(arg("rtt", "300"));
const DOWN = Number(arg("down", "800"));
const UP = Number(arg("up", "300"));
const RUNS = Number(arg("runs", "5"));
const OUT = arg("json");
const NO_SAVE = process.argv.includes("--no-save");
const SHAPER = process.argv.includes("--shaper");
const DIST = arg("dist");
const ONLY = arg("only"); // comma list of flow-name prefixes, for quick looks
if (!BASE) {
    console.error("--base is required");
    process.exit(2);
}
const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const { chromium } = createRequire(join(repo, "web/package.json"))("playwright-core");

const route = (u) => u.pathname.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, ":id");
const isApi = (u) => u.pathname.startsWith("/api/") || u.pathname === "/auth/login";

// Screens opened one after another from Today, each for the first time in the run.
const SCREENS = [
    ["Expenses & pay", "#/finance"],
    ["Stock", "#/inventory"],
    ["Clients", "#/clients"],
    ["Sales reports", "#/reports"],
    ["Remittance", "#/remittance"],
    ["My shifts & pay", "#/my-history"],
    ["Alerts", "#/notifications"],
    ["Relief duty", "#/relief"],
    ["Change history", "#/audit"],
    ["My profile", "#/profile"],
    ["Users", "#/users"],
    ["Branches", "#/branches"],
    ["Products", "#/catalog"],
];

let launchArgs = [];
let shaper;
if (SHAPER) {
    const b = new URL(BASE);
    shaper = await startShaper({ target: `${b.hostname}:${b.port || (b.protocol === "https:" ? 443 : 80)}`, rtt: RTT, down: DOWN, up: UP });
    if (b.protocol === "https:") launchArgs = [`--host-resolver-rules=MAP ${b.hostname} 127.0.0.1:${shaper.port}`];
    else BASE = `http://127.0.0.1:${shaper.port}`;
}
const TYPES = { ".js": "text/javascript", ".css": "text/css", ".html": "text/html", ".svg": "image/svg+xml", ".png": "image/png", ".webmanifest": "application/manifest+json" };
async function serveDist(ctx) {
    await ctx.route(
        (u) => u.origin === new URL(BASE).origin && !/^\/(api|auth|health)\b/.test(u.pathname),
        (r) => {
            const p = new URL(r.request().url()).pathname;
            const f = join(DIST, p === "/" || !existsSync(join(DIST, p)) ? "index.html" : p);
            const ext = f.slice(f.lastIndexOf("."));
            return r.fulfill({ body: readFileSync(f), contentType: TYPES[ext] ?? "application/octet-stream" });
        },
    );
}

async function oneRun() {
    const browser = await chromium.launch({ args: launchArgs });
    const ctx = await browser.newContext({ viewport: { width: 375, height: 812 }, timezoneId: "Asia/Manila", serviceWorkers: DIST ? "block" : "allow" });
    if (DIST) await serveDist(ctx);
    const page = await ctx.newPage();
    const cdp = await ctx.newCDPSession(page);
    await cdp.send("Network.enable");
    await cdp.send("Network.setCacheDisabled", { cacheDisabled: false });
    if (!SHAPER)
        await cdp.send("Network.emulateNetworkConditions", {
            offline: false,
            latency: RTT,
            downloadThroughput: (DOWN * 1000) / 8,
            uploadThroughput: (UP * 1000) / 8,
        });
    page.on("dialog", (d) => d.accept());

    let reqs = new Map(); // requestId -> {what, api, start, end, bytes}
    let open = 0;
    let lastActivity = Date.now();
    const now = () => Date.now();
    cdp.on("Network.requestWillBeSent", (e) => {
        if (reqs.has(e.requestId) && e.redirectResponse) return;
        const u = new URL(e.request.url);
        if (!/^https?:/.test(u.protocol)) return;
        reqs.set(e.requestId, { what: `${e.request.method} ${route(u)}`, api: isApi(u), start: now(), end: NaN, bytes: 0 });
        open++;
        lastActivity = now();
    });
    cdp.on("Network.dataReceived", (e) => {
        const r = reqs.get(e.requestId);
        if (r) r.bytes += 0;
    });
    const finish = (e, failed) => {
        const r = reqs.get(e.requestId);
        if (!r || !Number.isNaN(r.end)) return;
        r.end = now();
        if (!failed) r.bytes = e.encodedDataLength ?? 0;
        open--;
        lastActivity = now();
    };
    cdp.on("Network.loadingFinished", (e) => finish(e, false));
    cdp.on("Network.loadingFailed", (e) => finish(e, true));

    const settle = async (quietMs = 1200, maxMs = 25000) => {
        const t = now();
        while (now() - t < maxMs) {
            await page.waitForTimeout(100);
            if (open === 0 && now() - lastActivity >= quietMs) return;
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
    // `act` starts the flow; `usable` resolves when the person could use the screen.
    const flow = async (name, act, usable) => {
        reqs = new Map();
        const t0 = now();
        let usableMs = NaN;
        await act();
        if (usable) {
            await usable().then(
                () => (usableMs = now() - t0),
                () => {},
            );
        }
        await settle();
        const cs = [...reqs.values()].filter((r) => !Number.isNaN(r.end));
        const api = cs.filter((c) => c.api);
        const lastApi = api.length ? Math.max(...api.map((c) => c.end - t0)) : 0;
        flows[name] = {
            usableMs,
            settledMs: lastApi,
            calls: api.length,
            rounds: rounds(api),
            apiBytes: api.reduce((a, c) => a + c.bytes, 0),
            allBytes: cs.reduce((a, c) => a + c.bytes, 0),
            requests: cs.length,
            list: api.map((c) => c.what),
        };
    };
    // Content shown and no spinner left: the screen is usable.
    const screenUsable = () =>
        page.waitForFunction(
            () => {
                const m = document.querySelector("main");
                return !!m && !!m.querySelector("h1, h2, table, form") && !m.querySelector(".spin");
            },
            null,
            { timeout: 30000, polling: 50 },
        );
    const clockButton = () =>
        page
            .getByRole("banner")
            .getByRole("button", { name: /Clock (in|out)/ })
            .first()
            .waitFor({ timeout: 30000 });
    const go = async (hash) => {
        await page.evaluate((h) => (location.hash = h), hash);
    };
    const want = (name) => !ONLY || ONLY.split(",").some((p) => name.startsWith(p));

    // Cold first visit: nothing saved on the phone.
    await flow("first visit: login page", () => page.goto(BASE + "/"), () => page.getByLabel("Username").waitFor({ timeout: 30000 }));
    await page.getByLabel("Username").fill(USER);
    await page.getByLabel("Password").fill(PASS);
    await flow(
        "sign-in to Today",
        () => page.getByRole("button", { name: "Sign in" }).click(),
        async () => {
            await clockButton();
            await screenUsable();
        },
    );
    const bar = page.getByRole("banner");
    if (await bar.getByRole("button", { name: "Clock in" }).count()) {
        await bar.getByRole("button", { name: "Clock in" }).first().click();
        await bar.getByRole("button", { name: /Clock out/ }).first().waitFor({ timeout: 30000 });
        await settle();
    }

    // Phone saved the app and last data: the next open (the common case).
    await flow(
        "reload (signed in)",
        () => page.reload(),
        async () => {
            await clockButton();
            await screenUsable();
        },
    );

    for (const [name, hash] of SCREENS) {
        if (!want(`open ${name}`)) continue;
        const menu = await page.locator('nav[aria-label="Main"]').innerText();
        if (name !== "Alerts" && !menu.includes(name)) continue; // the person may not see this screen
        await flow(`open ${name}`, () => go(hash), screenUsable);
    }
    await flow("back to Today", () => go("#/today"), screenUsable);

    if (!NO_SAVE && want("save")) {
        // A client may hold one waiting session only: finish a leftover one from an earlier run first (not counted).
        const doneBtn = page.getByRole("row").filter({ hasText: "Probe" }).getByRole("button", { name: "Done" });
        while (await doneBtn.count()) {
            await doneBtn.first().click();
            await settle();
        }
        await page.getByRole("button", { name: "New session" }).first().click();
        const dialog = page.getByRole("dialog", { name: "New session" });
        await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill("Probe");
        const row = dialog.getByRole("row").filter({ hasText: "Probe" }).first();
        if (!(await row.waitFor({ timeout: 6000 }).then(() => true, () => false))) {
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
            const el = [...document.querySelectorAll("dialog[open] input")].find((i) => /price/i.test(i.labels?.[0]?.textContent ?? ""));
            return !!el && el.value !== "";
        });
        await settle();
        await flow(
            "save a session",
            () => dialog.getByRole("button", { name: "Add session" }).click(),
            () => dialog.waitFor({ state: "hidden", timeout: 30000 }),
        );
        await flow("mark the session Done", async () => {
            const r = page.getByRole("row").filter({ hasText: "Probe" }).filter({ has: page.getByRole("button", { name: "Done" }) }).first();
            await r.getByRole("button", { name: "Done" }).click();
        });
    }
    await browser.close();
    return flows;
}

const med = (xs) => {
    const v = xs.filter((x) => Number.isFinite(x)).sort((a, b) => a - b);
    return v.length ? v[Math.floor((v.length - 1) / 2)] : NaN;
};
const spread = (xs) => {
    const v = xs.filter((x) => Number.isFinite(x));
    return v.length ? `${Math.min(...v)}-${Math.max(...v)}` : "-";
};
const kb = (b) => (b / 1024).toFixed(1);

const results = [];
let errors = 0;
for (let i = 1; i <= RUNS; i++) {
    try {
        results.push(await oneRun());
    } catch (e) {
        errors++;
        console.error(`run ${i} failed: ${e.message.split("\n")[0]}`);
    }
}
await shaper?.close();
console.log(`\nhotspot: ${BASE}${SHAPER ? " (shaper)" : ""}${DIST ? ` dist ${DIST}` : ""}  rtt ${RTT}ms  down ${DOWN}kbit/s  up ${UP}kbit/s  runs ${results.length}/${RUNS} (errors ${errors})`);
console.log("flow".padEnd(30), "usable ms (min-max)".padEnd(22), "settled ms".padEnd(8), "calls".padEnd(7), "rounds".padEnd(7), "api KB".padEnd(8), "all KB");
const names = [...new Set(results.flatMap((r) => Object.keys(r)))];
let work = 0;
for (const n of names) {
    const rs = results.map((r) => r[n]).filter(Boolean);
    work += rs.length;
    const f = (k) => rs.map((r) => r[k]);
    console.log(
        n.padEnd(30),
        (Number.isNaN(med(f("usableMs"))) ? "-" : `${med(f("usableMs")).toFixed(0)} (${spread(f("usableMs"))})`).padEnd(22),
        `${med(f("settledMs")).toFixed(0)}`.padEnd(8),
        `${med(f("calls"))}`.padEnd(7),
        `${med(f("rounds"))}`.padEnd(7),
        `${kb(med(f("apiBytes")))}`.padEnd(8),
        kb(med(f("allBytes"))),
    );
}
console.log(`flows measured: ${work}, run errors: ${errors}`);
const first = results[0];
if (first) {
    console.log("\ncalls in run 1:");
    for (const [n, v] of Object.entries(first)) {
        const counts = new Map();
        for (const w of v.list) counts.set(w, (counts.get(w) ?? 0) + 1);
        console.log(`  ${n}: ${[...counts].map(([w, c]) => (c > 1 ? `${c}x ` : "") + w).join(" | ") || "(none)"}`);
    }
}
if (OUT) writeFileSync(OUT, JSON.stringify(results, null, 2));
if (errors || !results.length) process.exit(1);
