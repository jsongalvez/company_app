// Where the main thread spends a tap: signs in on a 375px phone with Chromium's CPU slowdown, then for each
// tap (menu, a screen, menu, Sessions, as vitals.mjs does) records its Event Timing duration and a CPU profile,
// and prints the functions with the most self time, mapped to web/src through the build's sourcemaps.
// No network shaping: this isolates script and render time from the network.
//   node tools/perf/tap-profile.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass [--cpu 4] [--screen Expenses] [--top 12] [--runs 3]
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const req = createRequire(join(repo, "web/package.json"));
const { chromium } = req("playwright-core");
const { SourceMapConsumer } = req("source-map-js");

const arg = (name, dflt) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : dflt;
};
const BASE = arg("base", "http://127.0.0.1:18181");
const USER = arg("user", process.env.CALLS_USER ?? "owner");
const PASS = arg("pass", process.env.CALLS_PASS ?? "owner-dev-pass");
const CPU = Number(arg("cpu", "4"));
const TOP = Number(arg("top", "12"));
const RUNS = Number(arg("runs", "3"));
const SCREEN = new RegExp(arg("screen", "Expenses|Finance"));

const maps = new Map();
async function mapFor(url) {
    if (!url.includes("/assets/") || !url.endsWith(".js")) return null;
    if (!maps.has(url))
        maps.set(
            url,
            fetch(`${url}.map`)
                .then((r) => (r.ok ? r.json() : null))
                .then((m) => (m ? new SourceMapConsumer(m) : null))
                .catch(() => null),
        );
    return maps.get(url);
}

async function where(frame) {
    const m = await mapFor(frame.url);
    if (!m)
        return `${frame.functionName || "(anon)"} ${frame.url.replace(/^.*\//, "") || "(native)"}`;
    const o = m.originalPositionFor({
        line: frame.lineNumber + 1,
        column: frame.columnNumber,
    });
    const src = (o.source ?? "?").replace(/^.*?(src\/|node_modules\/)/, "$1");
    return `${o.name || frame.functionName || "(anon)"} ${src}:${o.line}`;
}

async function selfTimes(profile) {
    const byId = new Map(profile.nodes.map((n) => [n.id, n]));
    const self = new Map();
    for (let i = 0; i < profile.samples.length; i++) {
        const dt = (profile.timeDeltas[i + 1] ?? 0) / 1000;
        self.set(profile.samples[i], (self.get(profile.samples[i]) ?? 0) + dt);
    }
    const out = new Map();
    for (const [id, ms] of self) {
        const f = byId.get(id).callFrame;
        if (["(idle)", "(program)", "(root)"].includes(f.functionName))
            continue;
        const key = f.url ? await where(f) : `${f.functionName} (native)`;
        out.set(key, (out.get(key) ?? 0) + ms);
    }
    return out;
}

const browser = await chromium.launch();
const totals = new Map();
const durations = {};
for (let run = 0; run < RUNS; run++) {
    const ctx = await browser.newContext({
        viewport: { width: 375, height: 812 },
        timezoneId: "Asia/Manila",
    });
    await ctx.addInitScript(() => {
        window.__ev = [];
        new PerformanceObserver((l) =>
            l
                .getEntries()
                .forEach(
                    (e) =>
                        e.interactionId &&
                        window.__ev.push([e.interactionId, e.duration]),
                ),
        ).observe({ type: "event", buffered: true, durationThreshold: 16 });
    });
    const page = await ctx.newPage();
    const cdp = await ctx.newCDPSession(page);
    await page.goto(BASE);
    await page.getByLabel("Username").fill(USER);
    await page.getByLabel("Password").fill(PASS);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page
        .getByRole("button", { name: "Menu" })
        .waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: CPU });
    await cdp.send("Profiler.enable");
    await cdp.send("Profiler.setSamplingInterval", { interval: 200 });
    const taps = [
        ["menu", () => page.getByRole("button", { name: "Menu" })],
        [
            "screen",
            () =>
                page
                    .locator('nav[aria-label="Main"] a', { hasText: SCREEN })
                    .first(),
        ],
        ["menu again", () => page.getByRole("button", { name: "Menu" })],
        [
            "Sessions",
            () =>
                page
                    .locator('nav[aria-label="Main"] a', {
                        hasText: "Sessions",
                    })
                    .first(),
        ],
    ];
    for (const [what, loc] of taps) {
        await page.evaluate(() => (window.__ev = []));
        await cdp.send("Profiler.start");
        await loc().click({ timeout: 15000 });
        await page.waitForTimeout(1200);
        const { profile } = await cdp.send("Profiler.stop");
        const d = await page.evaluate(() =>
            Math.max(0, ...window.__ev.map((e) => e[1])),
        );
        (durations[what] ??= []).push(d);
        for (const [k, ms] of await selfTimes(profile)) {
            const t = totals.get(what) ?? new Map();
            t.set(k, (t.get(k) ?? 0) + ms / RUNS);
            totals.set(what, t);
        }
    }
    await ctx.close();
}
await browser.close();

for (const [what, t] of totals) {
    const ds = durations[what].sort((a, b) => a - b);
    const total = [...t.values()].reduce((a, b) => a + b, 0);
    console.log(
        `\n== tap ${what}: event ${ds[Math.floor((ds.length - 1) / 2)]} ms median (${ds.join(", ")}), script+render self ${total.toFixed(0)} ms/run at ${CPU}x CPU`,
    );
    for (const [k, ms] of [...t].sort((a, b) => b[1] - a[1]).slice(0, TOP))
        console.log(`  ${ms.toFixed(1).padStart(7)} ms  ${k}`);
}
