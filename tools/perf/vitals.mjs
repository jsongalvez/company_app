// Web vitals on a slow phone hotspot, the way Lighthouse's mobile run measures them, but signed in.
//   node tools/perf/vitals.mjs --base URL [--user U --pass P] [--runs 5] [--cpu 4] [--rtt 300] [--down 800] [--up 300] [--json out.json]
// Network: the profile of hotspot.mjs, applied by shaper.mjs to every byte (handshakes and service worker
// fetches included, which Chromium's own emulation skips). --cpu is Chromium's CPU slowdown (Lighthouse mobile uses 4).
// Loads measured, each a fresh page load with its own vitals:
//   cold login     nothing saved on the phone (first visit, or after clearing the browser)
//   cold Today     signed in, but no saved app files (first open after a deploy with no service worker copy)
//   warm Today     signed in, service worker and HTTP cache warm (the everyday open)
// Per load: TTFB, FCP, LCP, CLS, TBT (long-task time over 50 ms after FCP until the network goes quiet),
// usable (Today: clock button and content, no spinner), JS/CSS KB over the wire, and asset requests served
// from the network vs a cache. INP comes from scripted taps on the warm page (menu, Expenses & pay, back to Sessions):
// the slowest tap's Event Timing duration. Medians of --runs; min-max is the noise band.
import { createRequire } from "node:module";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
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
const CPU = Number(arg("cpu", "4"));
const RUNS = Number(arg("runs", "5"));
const OUT = arg("json");
if (!BASE) {
    console.error("--base is required");
    process.exit(2);
}
const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const { chromium } = createRequire(join(repo, "web/package.json"))("playwright-core");

const OBSERVE = () => {
    const v = (window.__vitals = { lcp: 0, shifts: [], long: [], events: [] });
    const watch = (type, fn, extra = {}) => {
        try {
            new PerformanceObserver((l) => l.getEntries().forEach(fn)).observe({ type, buffered: true, ...extra });
        } catch {}
    };
    watch("largest-contentful-paint", (e) => (v.lcp = e.startTime));
    watch("layout-shift", (e) => !e.hadRecentInput && v.shifts.push([e.startTime, e.value]));
    watch("longtask", (e) => v.long.push([e.startTime, e.duration]));
    watch("event", (e) => e.interactionId && v.events.push([e.interactionId, e.duration, e.name]), { durationThreshold: 16 });
};

async function newPhone(browser, storageState) {
    const ctx = await browser.newContext({ viewport: { width: 375, height: 812 }, timezoneId: "Asia/Manila", serviceWorkers: "allow", storageState });
    await ctx.addInitScript(OBSERVE);
    const page = await ctx.newPage();
    page.on("dialog", (d) => d.accept());
    const cdp = await ctx.newCDPSession(page);
    await cdp.send("Network.enable");
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: CPU });
    const net = { open: 0, last: Date.now(), assets: [] };
    const types = new Map();
    cdp.on("Network.requestWillBeSent", (e) => {
        net.open++;
        net.last = Date.now();
        types.set(e.requestId, { url: e.request.url, bytes: 0, cached: false });
    });
    cdp.on("Network.responseReceived", (e) => {
        const r = types.get(e.requestId);
        if (r) r.cached = e.response.fromServiceWorker || e.response.fromDiskCache || e.response.fromPrefetchCache || false;
    });
    cdp.on("Network.requestServedFromCache", (e) => {
        const r = types.get(e.requestId);
        if (r) r.cached = true;
    });
    const done = (e) => {
        net.open = Math.max(0, net.open - 1);
        net.last = Date.now();
        const r = types.get(e.requestId);
        if (!r) return;
        r.bytes = e.encodedDataLength ?? 0;
        const p = new URL(r.url).pathname;
        if (/\.(js|css)$/.test(p) && !/sw\.js$/.test(p)) net.assets.push(r);
    };
    cdp.on("Network.loadingFinished", done);
    cdp.on("Network.loadingFailed", done);
    const quiet = async (ms = 1500, max = 30000) => {
        const t = Date.now();
        while (Date.now() - t < max) {
            await page.waitForTimeout(100);
            if (net.open === 0 && Date.now() - net.last >= ms) return;
        }
    };
    return { ctx, page, net, quiet };
}

const todayUsable = (page) =>
    page.waitForFunction(
        () => {
            const bar = document.querySelector("header");
            const m = document.querySelector("main");
            return !!bar && /Clock (in|out)/.test(bar.textContent ?? "") && !!m?.querySelector("h1, h2, table, form") && !m.querySelector(".spin");
        },
        null,
        { timeout: 40000, polling: 50 },
    );

async function readLoad(phone, usableFn) {
    const { page, net, quiet } = phone;
    net.assets = [];
    const t0 = Date.now();
    await page.goto(BASE + "/", { waitUntil: "commit" });
    let usable = NaN;
    if (usableFn) await usableFn(page).then(() => (usable = Date.now() - t0), () => {});
    await quiet();
    const v = await page.evaluate(() => {
        const nav = performance.getEntriesByType("navigation")[0];
        const fcp = performance.getEntriesByName("first-contentful-paint")[0]?.startTime ?? NaN;
        const w = window.__vitals;
        let cls = 0;
        let cur = 0;
        let start = -Infinity;
        let prev = -Infinity;
        for (const [t, val] of w.shifts) {
            if (t - prev > 1000 || t - start > 5000) {
                cur = 0;
                start = t;
            }
            cur += val;
            prev = t;
            cls = Math.max(cls, cur);
        }
        const tbt = w.long.filter(([t]) => t >= fcp).reduce((a, [, d]) => a + Math.max(0, d - 50), 0);
        return { ttfb: nav ? nav.responseStart : NaN, fcp, lcp: w.lcp || NaN, cls, tbt };
    });
    const kb = (re) => net.assets.filter((a) => re.test(new URL(a.url).pathname)).reduce((s, a) => s + a.bytes, 0) / 1024;
    return {
        ...v,
        usable,
        jsKB: kb(/\.js$/),
        cssKB: kb(/\.css$/),
        assetsNet: net.assets.filter((a) => !a.cached).length,
        assetsCached: net.assets.filter((a) => a.cached).length,
    };
}

async function inp(phone) {
    const { page, quiet } = phone;
    await page.evaluate(() => (window.__vitals.events = []));
    const tap = async (loc, what) => {
        await loc.click({ timeout: 15000 }).catch((e) => {
            throw new Error(`tap ${what}: ${e.message.split("\n")[0]}`);
        });
        await page.waitForTimeout(400);
    };
    await tap(page.getByRole("button", { name: "Menu" }), "menu");
    await tap(page.locator('nav[aria-label="Main"] a', { hasText: /Expenses|Finance|Sales reports|Clients/ }).first(), "screen");
    await quiet(800);
    await tap(page.getByRole("button", { name: "Menu" }), "menu again");
    await tap(page.locator('nav[aria-label="Main"] a', { hasText: "Sessions" }).first(), "Sessions");
    await quiet(800);
    return page.evaluate(() => {
        const per = new Map();
        for (const [id, d] of window.__vitals.events) per.set(id, Math.max(per.get(id) ?? 0, d));
        return { inp: per.size ? Math.max(...per.values()) : NaN, taps: per.size };
    });
}

async function oneRun(browser) {
    const out = {};
    const a = await newPhone(browser);
    out["cold login"] = await readLoad(a, (p) => p.getByLabel("Username").waitFor({ timeout: 40000 }));
    await a.page.getByLabel("Username").fill(USER);
    await a.page.getByLabel("Password").fill(PASS);
    await a.page.getByRole("button", { name: "Sign in" }).click();
    await todayUsable(a.page);
    await a.quiet();
    const state = await a.ctx.storageState();
    out["warm Today"] = await readLoad(a, todayUsable);
    Object.assign(out["warm Today"], await inp(a));
    await a.ctx.close();
    const b = await newPhone(browser, state);
    out["cold Today"] = await readLoad(b, todayUsable);
    await b.ctx.close();
    return out;
}

const med = (xs) => {
    const v = xs.filter(Number.isFinite).sort((x, y) => x - y);
    return v.length ? v[Math.floor((v.length - 1) / 2)] : NaN;
};
const band = (xs) => {
    const v = xs.filter(Number.isFinite);
    return v.length ? `${Math.round(Math.min(...v))}-${Math.round(Math.max(...v))}` : "-";
};

const base = new URL(BASE);
const remotePort = base.port || (base.protocol === "https:" ? "443" : "80");
const shaper = await startShaper({ target: `${base.hostname}:${remotePort}`, rtt: RTT, down: DOWN, up: UP });
const args = [];
if (base.protocol === "https:") args.push(`--host-resolver-rules=MAP ${base.hostname} 127.0.0.1:${shaper.port}`);
else BASE = `${base.protocol}//127.0.0.1:${shaper.port}`;
const browser = await chromium.launch({ args });
const runs = [];
let errors = 0;
for (let i = 0; i < RUNS; i++) {
    try {
        runs.push(await oneRun(browser));
    } catch (e) {
        errors++;
        console.error(`run ${i + 1} failed: ${e.message.split("\n")[0]}`);
    }
}
await browser.close();
await shaper.close();
console.log(`vitals: ${BASE}  rtt ${RTT}ms down ${DOWN}kbit/s up ${UP}kbit/s cpu ${CPU}x  runs ${runs.length}/${RUNS}`);
const cols = ["ttfb", "fcp", "lcp", "usable", "tbt", "cls", "inp", "jsKB", "cssKB", "assetsNet", "assetsCached"];
console.log("load".padEnd(12), ...cols.map((c) => c.padEnd(c === "cls" ? 7 : 15)));
for (const name of ["cold login", "cold Today", "warm Today"]) {
    const rs = runs.map((r) => r[name]).filter(Boolean);
    const cell = (c) => {
        const xs = rs.map((r) => r[c]);
        const m = med(xs);
        if (!Number.isFinite(m)) return "-".padEnd(15);
        if (c === "cls") return m.toFixed(3).padEnd(7);
        if (/KB$/.test(c)) return m.toFixed(1).padEnd(15);
        if (/^assets/.test(c)) return String(m).padEnd(15);
        return `${Math.round(m)} (${band(xs)})`.padEnd(15);
    };
    console.log(name.padEnd(12), ...cols.map(cell));
}
if (OUT) writeFileSync(OUT, JSON.stringify(runs, null, 2));
if (errors || !runs.length) process.exit(1);
