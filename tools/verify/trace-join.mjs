// Signs in and opens Today and Finance in a real browser, then checks that every /api call carried a W3C
// `traceparent` and that the server answered with the same trace id in X-Trace-Id (page and server share a trace).
//   node tools/verify/trace-join.mjs --base http://127.0.0.1:18181 [--user owner --pass owner-dev-pass] (or CALLS_USER, CALLS_PASS)
// Exit 0 when every call joined, 1 when any did not.
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const arg = (name, fallback) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg("base");
const USER = arg("user", process.env.CALLS_USER ?? "owner");
const PASS = arg("pass", process.env.CALLS_PASS ?? "owner-dev-pass");
if (!BASE) {
    console.error("--base is required");
    process.exit(2);
}
const repo = join(dirname(fileURLToPath(import.meta.url)), "../..");
const { chromium } = createRequire(join(repo, "web/package.json"))("playwright-core");

const TRACEPARENT = /^00-([0-9a-f]{32})-[0-9a-f]{16}-0[01]$/;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 375, height: 812 } });
const seen = [];
page.on("response", async (res) => {
    const req = res.request();
    const path = new URL(req.url()).pathname;
    if (!path.startsWith("/api/") && !path.startsWith("/auth/")) return;
    const sent = (await req.allHeaders())["traceparent"] ?? null;
    const sentId = sent?.match(TRACEPARENT)?.[1] ?? null;
    const answered = res.headers()["x-trace-id"] ?? null;
    seen.push({ call: `${req.method()} ${path}`, sent, joined: sentId !== null && sentId === answered });
});
await page.goto(BASE + "/");
await page.getByLabel("Username").fill(USER);
await page.getByLabel("Password").fill(PASS);
await page.getByRole("button", { name: "Sign in" }).click();
await page.getByRole("banner").getByRole("button", { name: /Clock (in|out)/ }).first().waitFor({ timeout: 30_000 });
await page.goto(BASE + "/#/finance");
await page.waitForLoadState("networkidle");
await browser.close();

for (const s of seen) console.log(`${s.joined ? "joined " : "MISSING"} ${s.call}${s.sent ? "" : " (no traceparent)"}`);
const missing = seen.filter((s) => !s.joined).length;
console.log(`${seen.length - missing}/${seen.length} calls share the page's trace id`);
process.exit(seen.length && !missing ? 0 : 1);
