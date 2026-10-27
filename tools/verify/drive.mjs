// node drive.mjs <baseUrl> <route e.g. #/today> <outDir> [name] [user] [pass]
// Signs in through the real login form, opens the route, waits for the page to settle,
// and saves a desktop (1400x860) and a phone (375x812) full-page screenshot. Prints the paths
// and any console errors / failed API calls seen on the page (exit 1 if there were any).
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { mkdirSync } from "node:fs";

const here = dirname(fileURLToPath(import.meta.url));
const { chromium } = createRequire(join(here, "../../web/package.json"))("playwright-core");
const [base, route, out, nameArg, user = "owner", pass = "owner-dev-pass"] = process.argv.slice(2);
const name = nameArg || route.replace(/^#?\/?/, "").replace(/[^a-z0-9]+/gi, "-") || "home";
const stamp = new Date().toISOString().replace(/[-:]/g, "").slice(0, 15);
mkdirSync(out, { recursive: true });

const sizes = { desktop: { width: 1400, height: 860 }, phone: { width: 375, height: 812 } };
const browser = await chromium.launch();
const problems = [];
const notes = []; // seen while signing in / clocking in, before the page under test is opened
const files = [];
try {
  for (const [label, viewport] of Object.entries(sizes)) {
    const ctx = await browser.newContext({ viewport, timezoneId: "Asia/Manila", deviceScaleFactor: label === "phone" ? 2 : 1 });
    const page = await ctx.newPage();
    page.on("dialog", (d) => d.accept()); // the app confirms clock in/out with confirm(), which Playwright dismisses by default
    let armed = false;
    const seen = (msg) => (armed ? problems : notes).push(msg);
    page.on("console", (m) => m.type() === "error" && seen(`[${label}] console: ${m.text()}`));
    page.on("pageerror", (e) => seen(`[${label}] pageerror: ${e.message}`));
    page.on("response", (r) => r.url().includes("/api/") && r.status() >= 400 && seen(`[${label}] ${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`));
    await page.goto(base + "/");
    await page.getByLabel("Username").fill(user);
    await page.getByLabel("Password").fill(pass);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.locator('nav[aria-label="Main"]').waitFor({ state: "attached" }); // collapsed on the phone, so a CSS locator: role queries skip hidden elements
    // boot() sets #/today after sign-in; wait for it so it cannot overwrite the route we open next.
    await page.waitForFunction(() => location.hash !== "");
    // Like a real shift: clock in at the current branch (the scratch DB's owner starts off duty, and
    // the day's sessions answer 403 until then). VERIFY_CLOCKIN=0 skips it.
    if (process.env.VERIFY_CLOCKIN !== "0") {
      const bar = page.getByRole("banner");
      await bar.getByRole("button", { name: /Clock (in|out)/ }).first().waitFor();
      const clockIn = bar.getByRole("button", { name: "Clock in" });
      if ((await clockIn.count()) > 0) await clockIn.first().click();
      await bar.getByRole("button", { name: /Clock out/ }).first().waitFor();
    }
    armed = true;
    await page.goto(base + "/" + route.replace(/^\//, ""));
    await page.evaluate(() => (location.hash = location.hash)); // same-document hash change needs no reload
    // A page already open when the shift started keeps its off-duty data (banner "Clock in at ... to see and log
    // today's sessions" stays up for 10s+, found with this harness). Reload so the shot shows what the server says now.
    if (process.env.VERIFY_CLOCKIN !== "0") await page.reload();
    await page.waitForLoadState("networkidle");
    // The app shows "Loading..." placeholders while data arrives; a shot of those proves nothing.
    try {
      await page.waitForFunction(() => !document.body.innerText.includes("Loading"), null, { timeout: 15000 });
    } catch {
      problems.push(`[${label}] still shows "Loading" after 15s`);
    }
    await page.waitForTimeout(300);
    const file = join(out, `${stamp}-${name}-${label}.png`);
    await page.screenshot({ path: file, fullPage: true });
    // Phone check: a page wider than the screen is a layout bug.
    const wide = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    if (label === "phone" && wide > 0) problems.push(`[phone] horizontal overflow ${wide}px`);
    console.log(`${label}: ${file} (${(await page.title()) || "no title"}; url ${page.url()})`);
    files.push(file);
    await ctx.close();
  }
} finally {
  await browser.close();
}
for (const n of notes) console.log("NOTE (before the page opened) " + n);
for (const p of problems) console.log("PROBLEM " + p);
process.exit(problems.length ? 1 : 0);
