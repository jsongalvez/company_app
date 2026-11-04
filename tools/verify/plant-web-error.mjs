// Plants one uncaught TypeError in a signed-in page (nothing is deployed or changed), waits for the page to
// upload it as telemetry, then polls ntfy for the web-error alert naming that page load's session id.
//   node tools/verify/plant-web-error.mjs --base https://api.kiel.party [--wait 90]
// Sign-in: CALLS_USER / CALLS_PASS (the probe user on prod: source ~/.config/company-app-probe.env and map
// PROBE_USER/PROBE_PASS). ntfy: NTFY_URL / NTFY_TOPIC (~/.config/company-app-alerts.env).
// Exit 0 when the alert arrived, 1 when it did not.
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const arg = (name, fallback) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg("base");
const WAIT_S = Number(arg("wait", "90"));
const { CALLS_USER: USER = "owner", CALLS_PASS: PASS = "owner-dev-pass", NTFY_URL, NTFY_TOPIC } = process.env;
if (!BASE || !NTFY_URL || !NTFY_TOPIC) {
    console.error("--base, NTFY_URL and NTFY_TOPIC are required");
    process.exit(2);
}
const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const { chromium } = createRequire(join(repo, "web/package.json"))("playwright-core");

const startedS = Math.floor(Date.now() / 1000);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 375, height: 812 } });
let sessionId = null;
let uploaded = false;
page.on("request", (r) => {
    if (!r.url().endsWith("/api/telemetry")) return;
    const body = JSON.parse(r.postData() ?? "{}");
    if (body.events?.some((e) => e.kind === "error" && e.data.includes("planted"))) sessionId = body.sessionId;
});
page.on("response", (r) => {
    if (r.url().endsWith("/api/telemetry") && sessionId && r.status() === 204) uploaded = true;
});
await page.goto(BASE + "/");
await page.getByLabel("Username").fill(USER);
await page.getByLabel("Password").fill(PASS);
await page.getByRole("button", { name: "Sign in" }).click();
await page.getByRole("banner").getByRole("button", { name: /Clock (in|out)/ }).first().waitFor({ timeout: 30_000 });
await page.evaluate(() => setTimeout(() => (null).planted()));
for (let i = 0; i < 40 && !uploaded; i++) await page.waitForTimeout(1000);
await browser.close();
console.log(uploaded ? `planted TypeError uploaded, session ${sessionId}` : "planted error never uploaded");
if (!uploaded) process.exit(1);

for (let waited = 0; waited <= WAIT_S; waited += 5) {
    const res = await fetch(`${NTFY_URL}/${NTFY_TOPIC}/json?poll=1&since=${startedS}`);
    const hit = (await res.text())
        .split("\n")
        .filter(Boolean)
        .map((l) => JSON.parse(l))
        .find((m) => m.event === "message" && m.message?.includes(sessionId));
    if (hit) {
        console.log(`ntfy alert after ${waited}s: [${hit.title}] ${hit.message.replaceAll("\n", " | ")}`);
        process.exit(0);
    }
    await new Promise((r) => setTimeout(r, 5000));
}
console.log(`no ntfy alert naming session ${sessionId} within ${WAIT_S}s`);
process.exit(1);
