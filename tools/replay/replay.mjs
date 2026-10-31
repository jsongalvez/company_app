// Plays a recorded page load (telemetry events) back in a real browser against a local copy of the app,
// then compares what the server answered with what the person got.
//   node tools/replay/replay.mjs --base URL --events events.json --user U --pass P --out DIR
// events.json: [{seq, kind, data}] in order, as tools/replay/replay.sh exports them. Taps and field changes
// are found with the recorder's own describe(), so a target matches when its hashes, href, name and
// position match what was recorded. Text fields get stand-in text of the recorded length.
import { createHmac } from "node:crypto";
import { createRequire } from "node:module";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const arg = (name, fallback) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg("base");
const OUT = arg("out", "/tmp/replay");
const USER = arg("user");
const PASS = arg("pass", "owner-dev-pass");
const recordedAll = JSON.parse(readFileSync(arg("events"), "utf8")).map(
    (e) => ({
        ...e,
        d: typeof e.data === "string" ? JSON.parse(e.data) : e.data,
    }),
);
// The replay starts signed in, so what the person did to sign in is not played again.
const signedInAt = recordedAll.findLastIndex(
    (e) => e.kind === "api" && e.d.p === "/auth/login" && e.d.s < 300,
);
const events = recordedAll.filter(
    (e, i) => e.kind === "boot" || i > signedInAt,
);
const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const tel = await import(join(repo, "web/src/lib/telemetry.ts"));
const { chromium } = createRequire(join(repo, "web/package.json"))(
    "playwright-core",
);
mkdirSync(OUT, { recursive: true });

const ID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
const shape = (m, p) => `${m} ${p.replace(ID, "{id}")}`;
const IGNORED = /^\/api\/telemetry/;

// The server stores each label hash keyed by the deployment's secret (LabelSeal.kt). LABEL_SECRET is that
// secret (prod's TELEMETRY_SALT, else its JWT_SECRET); without it, sealed labels can't match and the
// other keys decide.
const LABEL_KEY = process.env.LABEL_SECRET
    ? createHmac("sha256", process.env.LABEL_SECRET)
          .update("telemetry-labels")
          .digest("hex")
    : "";
const SEALED = /^[0-9a-f]{12}$/;
if (!LABEL_KEY && recordedAll.some((e) => SEALED.test(e.d?.el?.h ?? "")))
    console.warn(
        "sealed label hashes and no LABEL_SECRET: matching on position, href and name only",
    );

// The recorder's matching code, run inside the page.
const inPage = `
const TEXT_MAX = ${tel.TEXT_MAX}, PATH_DEPTH = ${tel.PATH_DEPTH}, ACTIONABLE = ${JSON.stringify(tel.ACTIONABLE)};
${tel.norm}
${tel.hash}
${tel.pathOf}
${tel.describe}
const LABEL_KEY = "${LABEL_KEY}", SEALED = ${SEALED};
const sealKey = LABEL_KEY && crypto.subtle.importKey("raw", Uint8Array.from(LABEL_KEY.match(/../g), (x) => parseInt(x, 16)), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
const seal = async (v) => {
    if (!sealKey || v === undefined) return v;
    const sig = new Uint8Array(await crypto.subtle.sign("HMAC", await sealKey, new TextEncoder().encode(v)));
    return [...sig].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 12);
};
window.__replayFind = async (want, n) => {
    const keys = ["h", "al", "href", "name", "role"];
    let best = null, bestScore = -1;
    for (const el of document.querySelectorAll(want.tag)) {
        if (!el.getClientRects().length) continue;
        const got = describe(el);
        for (const k of ["h", "al"]) if (SEALED.test(want[k] ?? "")) got[k] = await seal(got[k]);
        let score = got.tag === want.tag ? 1 : 0;
        for (const k of keys) if (want[k] !== undefined) score += got[k] === want[k] ? 3 : -2;
        if (got.path === want.path) score += 2;
        if (score > bestScore) { best = el; bestScore = score; }
    }
    if (!best) return null;
    best.setAttribute("data-replay", String(n));
    return bestScore;
};`;

const boot = events.find((e) => e.kind === "boot")?.d ?? {
    r: "#/",
    w: 1280,
    h: 800,
};
const browser = await chromium.launch();
const ctx = await browser.newContext({
    viewport: { width: boot.w || 1280, height: boot.h || 800 },
    deviceScaleFactor: 1,
});
const login = await ctx.request.post(`${BASE}/auth/login`, {
    data: { username: USER, password: PASS },
});
if (!login.ok())
    throw new Error(`sign-in as ${USER} failed: ${login.status()}`);
const { token } = await login.json();
await ctx.addInitScript((t) => localStorage.setItem("token", t), token);
await ctx.addInitScript(inPage);
const page = await ctx.newPage();
page.on("dialog", (d) => d.accept());
const seen = [];
const pageErrors = [];
page.on("response", (r) => {
    const u = new URL(r.url());
    if (u.pathname.startsWith("/api/") && !IGNORED.test(u.pathname))
        seen.push({
            call: shape(r.request().method(), u.pathname),
            s: r.status(),
        });
});
page.on("pageerror", (e) => pageErrors.push(String(e.message).slice(0, 200)));
const settle = () =>
    page.waitForLoadState("networkidle", { timeout: 4000 }).catch(() => {});

const steps = [];
let shot = 0;
const snap = async (label) => {
    const file = join(
        OUT,
        `${String(++shot).padStart(2, "0")}-${label.replace(/[^a-z0-9]+/gi, "-").slice(0, 40)}.png`,
    );
    await page.screenshot({ path: file });
    return file;
};

await page.goto(`${BASE}/${boot.r || "#/"}`);
await settle();
steps.push({
    seq: 0,
    kind: "boot",
    what: boot.r,
    ok: true,
    shot: await snap(`boot ${boot.r}`),
});

for (const e of events) {
    const d = e.d;
    if (e.kind === "nav") {
        const now = await page.evaluate(() => location.hash);
        if (tel.routeOf(now) !== d.r) {
            await page.evaluate((r) => (location.hash = r.slice(1)), d.r);
            await settle();
        }
        steps.push({
            seq: e.seq,
            kind: "nav",
            what: d.r,
            ok: true,
            shot: await snap(`nav ${d.r}`),
        });
    } else if (e.kind === "tap" || e.kind === "input") {
        const score = await page.evaluate(
            ([want, n]) => window.__replayFind(want, n),
            [d.el, e.seq],
        );
        const loc = page.locator(`[data-replay="${e.seq}"]`);
        let ok = score !== null && score > 0;
        let what = `${d.el.tag} ${d.el.path}${d.el.h ? ` h=${d.el.h}` : ""}`;
        try {
            if (!ok) throw new Error("no matching element");
            if (e.kind === "tap") await loc.click({ timeout: 3000 });
            else if (d.on !== undefined)
                await loc.setChecked(!!d.on, { timeout: 3000 });
            else if (d.type === "select-one") {
                if (d.val !== undefined)
                    await loc.selectOption(d.val, { timeout: 3000 });
                else await loc.selectOption({ index: 1 }, { timeout: 3000 });
                what += " (option)";
            } else {
                const text =
                    d.val ??
                    "Replay text that stands in for typing"
                        .padEnd(d.len ?? 0, "x")
                        .slice(0, d.len ?? 0);
                await loc.fill(String(text), { timeout: 3000 });
                what +=
                    d.val !== undefined
                        ? ` = ${d.val}`
                        : ` (${d.len ?? 0} stand-in chars)`;
            }
            await settle();
        } catch (err) {
            ok = false;
            what += ` FAILED: ${String(err.message).split("\n")[0]}`;
        }
        steps.push({ seq: e.seq, kind: e.kind, what, score, ok });
    }
}
const finalShot = await snap("end");
await browser.close();

const recorded = events
    .filter((e) => e.kind === "api" && !IGNORED.test(e.d.p))
    .map((e) => ({ call: shape(e.d.m, e.d.p), s: e.d.s }));
const pool = [...seen];
const compared = recorded.map((r) => {
    const i = pool.findIndex((x) => x.call === r.call);
    const got = i >= 0 ? pool.splice(i, 1)[0] : null;
    return { ...r, replay: got ? got.s : null, same: !!got && got.s === r.s };
});
const failures = compared.filter((c) => c.s >= 400 || c.s === 0);
const recordedErrors = events
    .filter((e) => e.kind === "error")
    .map((e) => e.d.msg);
const verdict =
    failures.length || recordedErrors.length
        ? failures.every((f) => f.same) &&
          recordedErrors.every((m) =>
              pageErrors.some((p) => p.includes(m.slice(0, 60))),
          )
            ? "REPRODUCED"
            : "NOT REPRODUCED"
        : "NO FAILURE RECORDED (judge the screenshots)";

const report = {
    verdict,
    steps,
    compared,
    extra: pool,
    recordedErrors,
    pageErrors,
    finalShot,
};
writeFileSync(join(OUT, "report.json"), JSON.stringify(report, null, 2));
const line = (c) =>
    `${c.call} recorded ${c.s} replay ${c.replay ?? "none"}${c.same ? "" : "  <-- differs"}`;
console.log(`verdict: ${verdict}`);
if (signedInAt >= 0)
    console.log(
        `sign-in (${signedInAt + 1} events) not played: the replay starts signed in`,
    );
console.log(
    `steps: ${steps.filter((s) => s.ok).length}/${steps.length} played${
        steps.some((s) => !s.ok)
            ? `; not played: ${steps
                  .filter((s) => !s.ok)
                  .map((s) => `#${s.seq} ${s.what}`)
                  .join("; ")}`
            : ""
    }`,
);
console.log(
    `api: ${compared.filter((c) => c.same).length}/${compared.length} same status${compared.length ? "\n  " + compared.map(line).join("\n  ") : ""}`,
);
if (pool.length)
    console.log(
        `extra calls on replay: ${pool.map((x) => `${x.call} ${x.s}`).join(", ")}`,
    );
if (recordedErrors.length || pageErrors.length)
    console.log(
        `errors recorded: ${JSON.stringify(recordedErrors)} replay: ${JSON.stringify(pageErrors)}`,
    );
console.log(`screenshots: ${OUT} (last: ${finalShot})`);
