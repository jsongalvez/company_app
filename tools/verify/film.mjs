// node film.mjs <baseUrl> <route> <outDir> [button...]
// Records every frame the browser paints (CDP screencast, phone size, CPU slowed 4x like a mid-range phone).
// No buttons: reloads the route VERIFY_FILM_RUNS times (default 4) and films each reload.
// Buttons: taps each one in turn (by accessible name, first match) and films what follows.
// A frame is blank when one colour covers nearly all of it: the page painted before the app drew anything.
// Saves each frame as <run>/NN_<ms after the reload or tap>.png and one strip image per run, prints a line
// per frame, and exits 1 when any frame after the start was blank.
// VERIFY_FILM_BEFORE=<shell command> runs once the phone has used the app (e.g. swap in a new web build, as a
// deploy does), then the route opens again, so `film '#/today' Update` films the update.
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { mkdirSync, writeFileSync } from "node:fs";
import { execSync } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const { chromium } = createRequire(join(here, "../../web/package.json"))(
    "playwright-core",
);
const [base, route, out, ...buttons] = process.argv.slice(2);
const user = process.env.VERIFY_USER || "owner";
const pass = process.env.VERIFY_PASS || "owner-dev-pass";
const runs = buttons.length ? 1 : Number(process.env.VERIFY_FILM_RUNS || 4);
const BLANK_SHARE = 0.995;
const name = route.replace(/^#?\/?/, "").replace(/[^a-z0-9]+/gi, "-") || "home";
const dir = join(
    out,
    `${new Date().toISOString().replace(/[-:]/g, "").slice(0, 15)}-film-${name}`,
);
mkdirSync(dir, { recursive: true });

const browser = await chromium.launch();
const ctx = await browser.newContext({
    viewport: { width: 375, height: 812 },
    timezoneId: "Asia/Manila",
});
const page = await ctx.newPage();
page.on("dialog", (d) => d.accept());
await page.goto(base + "/");
await page.getByLabel("Username").fill(user);
await page.getByLabel("Password").fill(pass);
await page.getByRole("button", { name: "Sign in" }).click();
await page.waitForFunction(() => location.hash !== "");
if (process.env.VERIFY_CLOCKIN !== "0") {
    const bar = page.getByRole("banner");
    await bar
        .getByRole("button", { name: /Clock (in|out)/ })
        .first()
        .waitFor();
    const clockIn = bar.getByRole("button", { name: "Clock in" });
    if ((await clockIn.count()) > 0) await clockIn.first().click();
    await bar
        .getByRole("button", { name: /Clock out/ })
        .first()
        .waitFor();
}
await page.goto(base + "/" + route.replace(/^\//, ""));
await page.waitForLoadState("networkidle");
// One reload first, so the service worker and the saved screen are in place like on a phone that used the app before.
await page.reload();
await page.waitForLoadState("networkidle");
await page.waitForTimeout(1500);
if (process.env.VERIFY_FILM_BEFORE) {
    execSync(process.env.VERIFY_FILM_BEFORE, { stdio: "inherit" });
    await page.reload();
    await page.waitForLoadState("networkidle");
}

const cdp = await ctx.newCDPSession(page);
await cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
let frames = [];
cdp.on("Page.screencastFrame", (f) => {
    frames.push({ t: Date.now(), png: Buffer.from(f.data, "base64") });
    cdp.send("Page.screencastFrameAck", { sessionId: f.sessionId }).catch(
        () => {},
    );
});

async function film(act) {
    frames = [];
    await cdp.send("Page.startScreencast", { format: "png", everyNthFrame: 1 });
    await page.waitForTimeout(300);
    const t0 = Date.now();
    await act();
    await page.waitForTimeout(2500);
    await cdp.send("Page.stopScreencast");
    return frames.map((f) => ({ ms: f.t - t0, png: f.png }));
}

const takes = buttons.length
    ? buttons.map((b) => ({
          label: `tap ${b}`,
          act: () => page.getByRole("button", { name: b }).first().click(),
      }))
    : Array.from({ length: runs }, (_, i) => ({
          label: `reload ${i + 1}`,
          act: () => page.reload({ waitUntil: "commit" }).catch(() => {}),
      }));
const filmed = [];
for (const take of takes)
    filmed.push({ ...take, frames: await film(take.act) });
await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });

// Share of pixels with the most common colour, measured in a blank page's canvas.
const judge = await ctx.newPage();
async function topColourShare(png) {
    return judge.evaluate(
        async (src) => {
            const img = new Image();
            img.src = src;
            await img.decode();
            const c = new OffscreenCanvas(img.width, img.height);
            const g = c.getContext("2d");
            g.drawImage(img, 0, 0);
            const px = g.getImageData(0, 0, img.width, img.height).data;
            const counts = new Map();
            for (let i = 0; i < px.length; i += 4) {
                const k = (px[i] << 16) | (px[i + 1] << 8) | px[i + 2];
                counts.set(k, (counts.get(k) || 0) + 1);
            }
            return Math.max(...counts.values()) / (px.length / 4);
        },
        "data:image/png;base64," + png.toString("base64"),
    );
}

let blankTakes = 0;
for (const [n, take] of filmed.entries()) {
    const runDir = join(dir, String(n + 1));
    mkdirSync(runDir);
    let blank = 0;
    const cells = [];
    console.log(`-- ${take.label}`);
    for (const [i, f] of take.frames.entries()) {
        const share = await topColourShare(f.png);
        // Frames from before the reload or tap are the old screen, not the thing under test.
        const isBlank = f.ms >= 0 && share >= BLANK_SHARE;
        if (isBlank) blank++;
        const file = `${String(i).padStart(2, "0")}_${f.ms}.png`;
        writeFileSync(join(runDir, file), f.png);
        console.log(
            `${file} ${(share * 100).toFixed(1)}% one colour${isBlank ? " BLANK" : ""}`,
        );
        cells.push(
            `<figure${isBlank ? ' class="blank"' : ""}><figcaption>${f.ms} ms${isBlank ? " BLANK" : ""}</figcaption><img src="data:image/png;base64,${f.png.toString("base64")}"></figure>`,
        );
    }
    if (blank) blankTakes++;
    const strip = join(dir, `strip-${n + 1}.png`);
    await judge.setViewportSize({
        width: Math.max(200, 200 * cells.length),
        height: 460,
    });
    await judge.setContent(
        `<style>body{margin:0;display:flex;font:12px sans-serif}figure{margin:2px;width:196px}img{width:188px;border:1px solid #888}.blank img{border:3px solid red}.blank figcaption{color:red;font-weight:bold}</style>${cells.join("")}`,
    );
    await judge.screenshot({ path: strip, fullPage: true });
    console.log(
        `${take.label}: ${take.frames.length} frames, ${blank} blank; strip ${strip}`,
    );
}
await browser.close();
console.log(
    `${route}: blank frames in ${blankTakes} of ${filmed.length} ${buttons.length ? "taps" : "reloads"} (frames in ${dir})`,
);
process.exit(blankTakes ? 1 : 0);
